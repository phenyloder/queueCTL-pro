package com.queuectl.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.queuectl.application.config.ConfigKeys;
import com.queuectl.application.model.ExecutionResult;
import com.queuectl.application.model.WorkerOptions;
import com.queuectl.application.service.WorkerControlService;
import com.queuectl.application.service.WorkerPoolService;
import com.queuectl.application.service.impl.JobServiceImpl;
import com.queuectl.application.service.impl.QueueConfigProviderImpl;
import com.queuectl.application.service.impl.WorkerControlServiceImpl;
import com.queuectl.application.service.impl.WorkerPoolServiceImpl;
import com.queuectl.application.spi.ClockProvider;
import com.queuectl.application.spi.JobRunner;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import com.queuectl.infra.config.DbSettings;
import com.queuectl.infra.db.DataSourceFactory;
import com.queuectl.infra.db.EntityManagerFactoryProvider;
import com.queuectl.infra.db.FlywayMigrator;
import com.queuectl.infra.db.repository.ConfigRepositoryImpl;
import com.queuectl.infra.db.repository.ControlRepositoryImpl;
import com.queuectl.infra.db.repository.DlqRepositoryImpl;
import com.queuectl.infra.db.repository.JobRepositoryImpl;
import com.queuectl.infra.db.repository.WorkerRepositoryImpl;
import com.queuectl.infra.metrics.NoopQueueMetrics;
import com.queuectl.infra.runner.ProcessJobRunner;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class QueueWorkerIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("queuectl")
          .withUsername("queuectl")
          .withPassword("queuectl");

  private static DataSource dataSource;
  private static EntityManagerFactory entityManagerFactory;
  private static JobRepositoryImpl jobRepository;
  private static DlqRepositoryImpl dlqRepository;
  private static ConfigRepositoryImpl configRepository;
  private static WorkerRepositoryImpl workerRepository;
  private static ControlRepositoryImpl controlRepository;

  private final ClockProvider clockProvider = Instant::now;
  private final NoopQueueMetrics metrics = new NoopQueueMetrics();

  @TempDir Path tempDir;

  @BeforeAll
  static void beforeAll() {
    POSTGRES.start();
    dataSource =
        DataSourceFactory.create(
            new DbSettings(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), 8));
    FlywayMigrator.migrate(dataSource);
    entityManagerFactory = EntityManagerFactoryProvider.create(dataSource);
    jobRepository = new JobRepositoryImpl(entityManagerFactory);
    dlqRepository = new DlqRepositoryImpl(entityManagerFactory);
    configRepository = new ConfigRepositoryImpl(entityManagerFactory);
    workerRepository = new WorkerRepositoryImpl(entityManagerFactory);
    controlRepository = new ControlRepositoryImpl(entityManagerFactory);
  }

  @AfterAll
  static void afterAll() throws Exception {
    if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
      entityManagerFactory.close();
    }
    if (dataSource instanceof AutoCloseable closeable) {
      closeable.close();
    }
    POSTGRES.stop();
  }

  @BeforeEach
  void setUp() {
    executeSql("DELETE FROM dlq");
    executeSql("DELETE FROM jobs");
    executeSql("DELETE FROM workers");
    executeSql("UPDATE control SET value='false', updated_at=NOW() WHERE key='shutdown_requested'");
    setDefaultConfig();
  }

  @AfterEach
  void tearDown() {
    executeSql("DELETE FROM workers");
    executeSql("UPDATE control SET value='false', updated_at=NOW() WHERE key='shutdown_requested'");
  }

  @Test
  void basicJobCompletesSuccessfully() throws Exception {
    Path outputDir = tempDir.resolve("outputs");
    var queueConfigProvider = new QueueConfigProviderImpl(configRepository);
    var jobService = new JobServiceImpl(jobRepository, clockProvider, metrics, queueConfigProvider);
    var workerControl = new WorkerControlServiceImpl(controlRepository, clockProvider);
    var workerPoolService =
        new WorkerPoolServiceImpl(
            jobRepository,
            dlqRepository,
            workerRepository,
            controlRepository,
            new ProcessJobRunner(outputDir),
            clockProvider,
            metrics,
            queueConfigProvider);

    JobDto job = jobService.enqueue("default", "echo", List.of("hello"), 1, Instant.now());

    WorkerRuntime runtime =
        startWorker(
            workerPoolService,
            new WorkerOptions(
                1,
                List.of("default"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMillis(50),
                9090));

    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              JobDto stored = jobRepository.getJob(job.id()).orElseThrow();
              assertEquals(JobState.COMPLETED, stored.state());
            });

    stopWorker(workerControl, runtime);

    JobDto completed = jobRepository.getJob(job.id()).orElseThrow();
    assertNotNull(completed.outputRef());
    assertTrue(Files.readString(Path.of(completed.outputRef())).contains("hello"));
  }

  @Test
  void failedJobRetriesWithBackoffAndMovesToDlq() throws Exception {
    Path outputDir = tempDir.resolve("outputs");
    configRepository.setConfig(ConfigKeys.ALLOWED_COMMANDS, "sh", Instant.now());
    configRepository.setConfig(ConfigKeys.BACKOFF_BASE, "1", Instant.now());
    configRepository.setConfig(ConfigKeys.MAX_DELAY_SECONDS, "1", Instant.now());

    var queueConfigProvider = new QueueConfigProviderImpl(configRepository);
    var jobService = new JobServiceImpl(jobRepository, clockProvider, metrics, queueConfigProvider);
    var workerControl = new WorkerControlServiceImpl(controlRepository, clockProvider);
    var workerPoolService =
        new WorkerPoolServiceImpl(
            jobRepository,
            dlqRepository,
            workerRepository,
            controlRepository,
            new ProcessJobRunner(outputDir),
            clockProvider,
            metrics,
            queueConfigProvider);

    JobDto job = jobService.enqueue("default", "sh", List.of("-c", "exit 1"), 1, Instant.now());

    WorkerRuntime runtime =
        startWorker(
            workerPoolService,
            new WorkerOptions(
                1,
                List.of("default"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMillis(50),
                9090));

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              JobDto stored = jobRepository.getJob(job.id()).orElseThrow();
              assertEquals(JobState.DEAD, stored.state());
              assertEquals(1L, dlqRepository.countDlq());
              assertEquals(2, stored.attempts());
            });

    stopWorker(workerControl, runtime);
  }

  @Test
  void multipleWorkersProcessWithoutOverlap() throws Exception {
    var queueConfigProvider = new QueueConfigProviderImpl(configRepository);
    var jobService = new JobServiceImpl(jobRepository, clockProvider, metrics, queueConfigProvider);
    var workerControl = new WorkerControlServiceImpl(controlRepository, clockProvider);
    RecordingRunner recordingRunner = new RecordingRunner(Duration.ofMillis(30));
    var workerPoolService =
        new WorkerPoolServiceImpl(
            jobRepository,
            dlqRepository,
            workerRepository,
            controlRepository,
            recordingRunner,
            clockProvider,
            metrics,
            queueConfigProvider);

    int jobCount = 30;
    for (int i = 0; i < jobCount; i++) {
      jobService.enqueue("default", "noop", List.of("j" + i), 1, Instant.now());
    }

    WorkerRuntime runtime =
        startWorker(
            workerPoolService,
            new WorkerOptions(
                3,
                List.of("default"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMillis(20),
                9090));

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              long completed = jobRepository.countByState().getOrDefault(JobState.COMPLETED, 0L);
              assertEquals(jobCount, completed);
            });

    stopWorker(workerControl, runtime);

    assertEquals(jobCount, recordingRunner.invocations().size());
    for (Integer count : recordingRunner.invocations().values()) {
      assertEquals(1, count);
    }
  }

  @Test
  void invalidCommandFailsGracefullyAndRetries() throws Exception {
    Path outputDir = tempDir.resolve("outputs");
    configRepository.setConfig(ConfigKeys.ALLOWED_COMMANDS, "doesnotexist", Instant.now());
    configRepository.setConfig(ConfigKeys.BACKOFF_BASE, "1", Instant.now());
    configRepository.setConfig(ConfigKeys.MAX_DELAY_SECONDS, "1", Instant.now());

    var queueConfigProvider = new QueueConfigProviderImpl(configRepository);
    var jobService = new JobServiceImpl(jobRepository, clockProvider, metrics, queueConfigProvider);
    var workerControl = new WorkerControlServiceImpl(controlRepository, clockProvider);
    var workerPoolService =
        new WorkerPoolServiceImpl(
            jobRepository,
            dlqRepository,
            workerRepository,
            controlRepository,
            new ProcessJobRunner(outputDir),
            clockProvider,
            metrics,
            queueConfigProvider);

    JobDto job = jobService.enqueue("default", "doesnotexist", List.of(), 1, Instant.now());

    WorkerRuntime runtime =
        startWorker(
            workerPoolService,
            new WorkerOptions(
                1,
                List.of("default"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMillis(50),
                9090));

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              JobDto stored = jobRepository.getJob(job.id()).orElseThrow();
              assertEquals(JobState.DEAD, stored.state());
              assertEquals(2, stored.attempts());
              assertTrue(
                  stored.lastError() != null && stored.lastError().contains("Cannot run program"));
            });

    stopWorker(workerControl, runtime);
  }

  @Test
  void jobsPersistAcrossWorkerRestart() throws Exception {
    var queueConfigProvider = new QueueConfigProviderImpl(configRepository);
    var jobService = new JobServiceImpl(jobRepository, clockProvider, metrics, queueConfigProvider);
    var workerControl = new WorkerControlServiceImpl(controlRepository, clockProvider);
    RecordingRunner recordingRunner = new RecordingRunner(Duration.ofMillis(250));
    var workerPoolService =
        new WorkerPoolServiceImpl(
            jobRepository,
            dlqRepository,
            workerRepository,
            controlRepository,
            recordingRunner,
            clockProvider,
            metrics,
            queueConfigProvider);

    int jobCount = 6;
    for (int i = 0; i < jobCount; i++) {
      jobService.enqueue("default", "noop", List.of("r" + i), 1, Instant.now());
    }

    WorkerOptions options =
        new WorkerOptions(
            1,
            List.of("default"),
            Duration.ofSeconds(30),
            Duration.ofSeconds(5),
            Duration.ofMillis(20),
            9090);

    WorkerRuntime firstRun = startWorker(workerPoolService, options);
    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              long completed = jobRepository.countByState().getOrDefault(JobState.COMPLETED, 0L);
              assertTrue(completed > 0);
            });
    stopWorker(workerControl, firstRun);

    long completedAfterFirstRun = jobRepository.countByState().getOrDefault(JobState.COMPLETED, 0L);
    assertTrue(completedAfterFirstRun < jobCount);

    WorkerRuntime secondRun = startWorker(workerPoolService, options);
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              long completed = jobRepository.countByState().getOrDefault(JobState.COMPLETED, 0L);
              assertEquals(jobCount, completed);
            });
    stopWorker(workerControl, secondRun);
  }

  private WorkerRuntime startWorker(WorkerPoolService workerPoolService, WorkerOptions options) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> future = executor.submit(() -> workerPoolService.runWorkers(options));
    return new WorkerRuntime(executor, future);
  }

  private void stopWorker(WorkerControlService workerControl, WorkerRuntime runtime)
      throws Exception {
    workerControl.requestStop();
    runtime.future.get();
    runtime.executor.shutdownNow();
  }

  private void setDefaultConfig() {
    Instant now = Instant.now();
    configRepository.setConfig(ConfigKeys.MAX_RETRIES, "3", now);
    configRepository.setConfig(ConfigKeys.BACKOFF_BASE, "2", now);
    configRepository.setConfig(ConfigKeys.MAX_DELAY_SECONDS, "300", now);
    configRepository.setConfig(ConfigKeys.ALLOWED_COMMANDS, "echo,sleep", now);
    configRepository.setConfig(ConfigKeys.JOB_TIMEOUT_SECONDS, "30", now);
    configRepository.setConfig(ConfigKeys.MAX_OUTPUT_BYTES, "1000000", now);
  }

  private static void executeSql(String sql) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to execute test SQL: " + sql, exception);
    }
  }

  private record WorkerRuntime(ExecutorService executor, Future<?> future) {}

  private static final class RecordingRunner implements JobRunner {

    private final Duration delay;
    private final Map<UUID, Integer> invocations = new ConcurrentHashMap<>();

    private RecordingRunner(Duration delay) {
      this.delay = delay;
    }

    @Override
    public ExecutionResult run(
        JobDto job, Duration timeout, Set<String> allowedCommands, long maxOutputBytes) {
      invocations.merge(job.id(), 1, Integer::sum);
      try {
        Thread.sleep(delay);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      }
      return new ExecutionResult(0, false, "memory://" + job.id(), null, null);
    }

    private Map<UUID, Integer> invocations() {
      return invocations;
    }
  }
}

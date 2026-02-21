package com.queuectl.cli.bootstrap;

import com.queuectl.application.service.ConfigService;
import com.queuectl.application.service.DlqService;
import com.queuectl.application.service.JobService;
import com.queuectl.application.service.QueueConfigProvider;
import com.queuectl.application.service.StatusService;
import com.queuectl.application.service.WorkerControlService;
import com.queuectl.application.service.WorkerPoolService;
import com.queuectl.application.service.impl.ConfigServiceImpl;
import com.queuectl.application.service.impl.DlqServiceImpl;
import com.queuectl.application.service.impl.JobServiceImpl;
import com.queuectl.application.service.impl.QueueConfigProviderImpl;
import com.queuectl.application.service.impl.StatusServiceImpl;
import com.queuectl.application.service.impl.WorkerControlServiceImpl;
import com.queuectl.application.service.impl.WorkerPoolServiceImpl;
import com.queuectl.application.spi.QueueMetrics;
import com.queuectl.infra.config.DbSettings;
import com.queuectl.infra.config.SystemClockProvider;
import com.queuectl.infra.db.DataSourceFactory;
import com.queuectl.infra.db.EntityManagerFactoryProvider;
import com.queuectl.infra.db.FlywayMigrator;
import com.queuectl.infra.db.repository.ConfigRepositoryImpl;
import com.queuectl.infra.db.repository.ControlRepositoryImpl;
import com.queuectl.infra.db.repository.DlqRepositoryImpl;
import com.queuectl.infra.db.repository.JobRepositoryImpl;
import com.queuectl.infra.db.repository.WorkerRepositoryImpl;
import com.queuectl.infra.metrics.MicrometerQueueMetrics;
import com.queuectl.infra.metrics.NoopQueueMetrics;
import com.queuectl.infra.metrics.PrometheusMetricsServer;
import com.queuectl.infra.runner.ProcessJobRunner;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import javax.sql.DataSource;

public final class QueueCtlContext implements AutoCloseable {

  private final JobService jobService;
  private final WorkerPoolService workerPoolService;
  private final WorkerControlService workerControlService;
  private final StatusService statusService;
  private final DlqService dlqService;
  private final ConfigService configService;

  private final DataSource dataSource;
  private final EntityManagerFactory entityManagerFactory;
  private final MicrometerQueueMetrics micrometerQueueMetrics;
  private final PrometheusMetricsServer metricsServer;

  private QueueCtlContext(
      DataSource dataSource,
      EntityManagerFactory entityManagerFactory,
      JobService jobService,
      WorkerPoolService workerPoolService,
      WorkerControlService workerControlService,
      StatusService statusService,
      DlqService dlqService,
      ConfigService configService,
      MicrometerQueueMetrics micrometerQueueMetrics,
      PrometheusMetricsServer metricsServer) {
    this.dataSource = dataSource;
    this.entityManagerFactory = entityManagerFactory;
    this.jobService = jobService;
    this.workerPoolService = workerPoolService;
    this.workerControlService = workerControlService;
    this.statusService = statusService;
    this.dlqService = dlqService;
    this.configService = configService;
    this.micrometerQueueMetrics = micrometerQueueMetrics;
    this.metricsServer = metricsServer;
  }

  public static QueueCtlContext create(boolean enableMetrics, Path outputDirectory) {
    DbSettings dbSettings = DbSettings.fromEnvironment();
    DataSource dataSource = DataSourceFactory.create(dbSettings);
    FlywayMigrator.migrate(dataSource);
    EntityManagerFactory entityManagerFactory = EntityManagerFactoryProvider.create(dataSource);

    JobRepositoryImpl jobRepository = new JobRepositoryImpl(entityManagerFactory);
    DlqRepositoryImpl dlqRepository = new DlqRepositoryImpl(entityManagerFactory);
    ConfigRepositoryImpl configRepository = new ConfigRepositoryImpl(entityManagerFactory);
    WorkerRepositoryImpl workerRepository = new WorkerRepositoryImpl(entityManagerFactory);
    ControlRepositoryImpl controlRepository = new ControlRepositoryImpl(entityManagerFactory);
    SystemClockProvider clockProvider = new SystemClockProvider();

    QueueMetrics queueMetrics;
    MicrometerQueueMetrics micrometer = null;
    PrometheusMetricsServer server = null;
    if (enableMetrics) {
      micrometer = new MicrometerQueueMetrics();
      server = new PrometheusMetricsServer();
      queueMetrics = micrometer;
    } else {
      queueMetrics = new NoopQueueMetrics();
    }

    QueueConfigProvider queueConfigProvider = new QueueConfigProviderImpl(configRepository);
    JobService jobService =
        new JobServiceImpl(jobRepository, clockProvider, queueMetrics, queueConfigProvider);
    WorkerPoolService workerPoolService =
        new WorkerPoolServiceImpl(
            jobRepository,
            dlqRepository,
            workerRepository,
            controlRepository,
            new ProcessJobRunner(outputDirectory),
            clockProvider,
            queueMetrics,
            queueConfigProvider);
    WorkerControlService workerControlService =
        new WorkerControlServiceImpl(controlRepository, clockProvider);
    StatusService statusService =
        new StatusServiceImpl(jobRepository, dlqRepository, workerRepository, clockProvider);
    DlqService dlqService = new DlqServiceImpl(dlqRepository, clockProvider);
    ConfigService configService = new ConfigServiceImpl(configRepository, clockProvider);

    return new QueueCtlContext(
        dataSource,
        entityManagerFactory,
        jobService,
        workerPoolService,
        workerControlService,
        statusService,
        dlqService,
        configService,
        micrometer,
        server);
  }

  public void startMetricsServer(int port) {
    if (micrometerQueueMetrics != null && metricsServer != null) {
      metricsServer.start(port, micrometerQueueMetrics);
    }
  }

  JobService jobService() {
    return jobService;
  }

  WorkerPoolService workerPoolService() {
    return workerPoolService;
  }

  WorkerControlService workerControlService() {
    return workerControlService;
  }

  StatusService statusService() {
    return statusService;
  }

  DlqService dlqService() {
    return dlqService;
  }

  ConfigService configService() {
    return configService;
  }

  @Override
  public void close() {
    if (metricsServer != null) {
      metricsServer.close();
    }
    if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
      entityManagerFactory.close();
    }
    if (dataSource instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception exception) {
        throw new IllegalStateException("Failed to close datasource", exception);
      }
    }
  }
}

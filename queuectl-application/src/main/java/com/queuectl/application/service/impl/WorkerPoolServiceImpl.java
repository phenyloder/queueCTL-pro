package com.queuectl.application.service.impl;

import com.queuectl.application.model.ExecutionResult;
import com.queuectl.application.model.QueueConfig;
import com.queuectl.application.model.WorkerOptions;
import com.queuectl.application.repository.ControlRepository;
import com.queuectl.application.repository.DlqRepository;
import com.queuectl.application.repository.JobRepository;
import com.queuectl.application.repository.WorkerRepository;
import com.queuectl.application.service.QueueConfigProvider;
import com.queuectl.application.service.WorkerPoolService;
import com.queuectl.application.spi.ClockProvider;
import com.queuectl.application.spi.JobRunner;
import com.queuectl.application.spi.QueueMetrics;
import com.queuectl.domain.JitterType;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkerPoolServiceImpl implements WorkerPoolService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkerPoolServiceImpl.class);

  private final JobRepository jobRepository;
  private final DlqRepository dlqRepository;
  private final WorkerRepository workerRepository;
  private final ControlRepository controlRepository;
  private final JobRunner jobRunner;
  private final ClockProvider clockProvider;
  private final QueueMetrics queueMetrics;
  private final QueueConfigProvider queueConfigProvider;

  public WorkerPoolServiceImpl(
      JobRepository jobRepository,
      DlqRepository dlqRepository,
      WorkerRepository workerRepository,
      ControlRepository controlRepository,
      JobRunner jobRunner,
      ClockProvider clockProvider,
      QueueMetrics queueMetrics,
      QueueConfigProvider queueConfigProvider) {
    this.jobRepository = jobRepository;
    this.dlqRepository = dlqRepository;
    this.workerRepository = workerRepository;
    this.controlRepository = controlRepository;
    this.jobRunner = jobRunner;
    this.clockProvider = clockProvider;
    this.queueMetrics = queueMetrics;
    this.queueConfigProvider = queueConfigProvider;
  }

  @Override
  public void runWorkers(WorkerOptions workerOptions) {
    if (workerOptions.count() < 1) {
      throw new IllegalArgumentException("worker count must be >= 1");
    }
    if (workerOptions.queues().isEmpty()) {
      throw new IllegalArgumentException("at least one queue is required");
    }

    controlRepository.setShutdownRequested(false, clockProvider.now());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      ArrayList<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < workerOptions.count(); i++) {
        UUID workerId = UUID.randomUUID();
        futures.add(
            executor.submit(
                () -> {
                  runSingleWorker(workerId, workerOptions);
                  return null;
                }));
      }

      for (Future<?> future : futures) {
        future.get();
      }
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      LOGGER.info("Worker pool interrupted, shutting down");
    } catch (Exception exception) {
      LOGGER.error("Worker execution failed", exception);
      throw new IllegalStateException("Worker execution failed", exception);
    }
  }

  private void runSingleWorker(UUID workerId, WorkerOptions workerOptions) {
    LOGGER.info("worker_started workerId={} queues={}", workerId, workerOptions.queues());
    Instant lastHeartbeat = Instant.EPOCH;
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Instant now = clockProvider.now();
        if (lastHeartbeat.plus(workerOptions.heartbeat()).isBefore(now)
            || lastHeartbeat.equals(Instant.EPOCH)) {
          workerRepository.upsertWorkerHeartbeat(workerId, workerOptions.queues(), now);
          refreshMetrics(now);
          lastHeartbeat = now;
        }

        if (controlRepository.isShutdownRequested()) {
          LOGGER.info("worker_shutdown_requested workerId={}", workerId);
          return;
        }

        UUID leaseId = UUID.randomUUID();
        List<JobDto> leasedJobs =
            jobRepository.leaseJobs(
                workerOptions.queues(), 1, leaseId, workerOptions.leaseTtl(), now);

        if (leasedJobs.isEmpty()) {
          sleep(workerOptions.poll());
          continue;
        }

        JobDto job = leasedJobs.get(0);
        queueMetrics.onLeased(job.queue());
        jobRepository.markProcessing(job.id(), leaseId, clockProvider.now());
        runLeasedJob(job, leaseId, workerOptions.leaseTtl());
      }
    } finally {
      workerRepository.removeWorkerHeartbeat(workerId);
      refreshMetrics(clockProvider.now());
      LOGGER.info("worker_stopped workerId={}", workerId);
    }
  }

  private void runLeasedJob(JobDto job, UUID leaseId, Duration leaseTtl) {
    QueueConfig queueConfig = queueConfigProvider.load((int) leaseTtl.getSeconds());
    Duration timeout = Duration.ofSeconds(Math.max(1, queueConfig.jobTimeoutSeconds()));

    LOGGER.info(
        "job_execution_started jobId={} queue={} command={}", job.id(), job.queue(), job.command());

    ExecutionResult result =
        jobRunner.run(job, timeout, queueConfig.allowedCommands(), queueConfig.maxOutputBytes());

    Instant now = clockProvider.now();
    if (result.isSuccess()) {
      boolean acknowledged = jobRepository.ackJob(job.id(), leaseId, now, result.outputRef());
      if (acknowledged) {
        queueMetrics.onCompleted(job.queue());
        LOGGER.info(
            "job_execution_completed jobId={} queue={} outputRef={}",
            job.id(),
            job.queue(),
            result.outputRef());
        refreshMetrics(now);
      }
      return;
    }

    int failedAttempt = job.attempts() + 1;
    String failure = result.describeFailure();

    if (failedAttempt > job.maxRetries()) {
      boolean deadMoved =
          jobRepository.moveToDead(job.id(), leaseId, now, failure, result.outputRef());
      if (deadMoved) {
        queueMetrics.onDead(job.queue());
        LOGGER.warn(
            "job_moved_to_dlq jobId={} attempts={} error={}", job.id(), failedAttempt, failure);
        refreshMetrics(now);
      }
      return;
    }

    Duration backoff =
        RetryPolicy.nextDelay(
            failedAttempt,
            queueConfig.backoffBase(),
            queueConfig.maxDelaySeconds(),
            JitterType.FULL,
            ThreadLocalRandom.current());
    Instant nextRunAt = now.plus(backoff);
    boolean nacked =
        jobRepository.nackJob(job.id(), leaseId, now, nextRunAt, failure, result.outputRef());
    if (nacked) {
      queueMetrics.onFailed(job.queue());
      LOGGER.warn(
          "job_nacked jobId={} attempts={} nextRunAt={} error={}",
          job.id(),
          failedAttempt,
          nextRunAt,
          failure);
      refreshMetrics(now);
    }
  }

  private void refreshMetrics(Instant now) {
    queueMetrics.refreshGauges(
        jobRepository.countByState(),
        dlqRepository.countDlq(),
        workerRepository.countActiveWorkers(Duration.ofSeconds(15), now));
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }
}

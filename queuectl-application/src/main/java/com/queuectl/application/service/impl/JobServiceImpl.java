package com.queuectl.application.service.impl;

import com.queuectl.application.repository.JobRepository;
import com.queuectl.application.service.JobService;
import com.queuectl.application.service.QueueConfigProvider;
import com.queuectl.application.spi.ClockProvider;
import com.queuectl.application.spi.QueueMetrics;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JobServiceImpl implements JobService {

  private final JobRepository jobRepository;
  private final ClockProvider clockProvider;
  private final QueueMetrics queueMetrics;
  private final QueueConfigProvider queueConfigProvider;

  public JobServiceImpl(
      JobRepository jobRepository,
      ClockProvider clockProvider,
      QueueMetrics queueMetrics,
      QueueConfigProvider queueConfigProvider) {
    this.jobRepository = jobRepository;
    this.clockProvider = clockProvider;
    this.queueMetrics = queueMetrics;
    this.queueConfigProvider = queueConfigProvider;
  }

  @Override
  public JobDto enqueue(
      String queue, String command, List<String> args, Integer maxRetries, Instant runAt) {
    Instant now = clockProvider.now();
    int effectiveRetries =
        maxRetries == null ? queueConfigProvider.load(30).defaultMaxRetries() : maxRetries;
    Instant effectiveRunAt = runAt == null ? now : runAt;
    JobDto job = jobRepository.enqueue(queue, command, args, effectiveRetries, effectiveRunAt, now);
    queueMetrics.onEnqueued(queue);
    return job;
  }

  @Override
  public List<JobDto> list(Optional<JobState> state, Optional<String> queue, int limit) {
    return jobRepository.listJobs(state, queue, limit);
  }

  @Override
  public Optional<JobDto> inspect(UUID jobId) {
    return jobRepository.getJob(jobId);
  }

  @Override
  public boolean cancel(UUID jobId) {
    return jobRepository.cancelJob(jobId, clockProvider.now());
  }
}

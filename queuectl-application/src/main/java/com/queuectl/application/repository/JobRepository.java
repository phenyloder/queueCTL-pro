package com.queuectl.application.repository;

import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {

  JobDto enqueue(
      String queue, String command, List<String> args, int maxRetries, Instant runAt, Instant now);

  List<JobDto> leaseJobs(
      List<String> queues, int limit, UUID leaseId, Duration leaseTtl, Instant now);

  boolean markProcessing(UUID jobId, UUID leaseId, Instant now);

  boolean ackJob(UUID jobId, UUID leaseId, Instant now, String outputRef);

  boolean nackJob(
      UUID jobId, UUID leaseId, Instant now, Instant nextRunAt, String lastError, String outputRef);

  boolean moveToDead(UUID jobId, UUID leaseId, Instant now, String reason, String outputRef);

  Optional<JobDto> getJob(UUID jobId);

  boolean cancelJob(UUID jobId, Instant now);

  List<JobDto> listJobs(Optional<JobState> state, Optional<String> queue, int limit);

  Map<JobState, Long> countByState();
}

package com.queuectl.cli.mvc.controller;

import com.queuectl.application.service.JobService;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JobController {

  private final JobService jobService;

  public JobController(JobService jobService) {
    this.jobService = jobService;
  }

  public JobDto enqueue(
      String queue, String command, List<String> args, Integer maxRetries, Instant runAt) {
    return jobService.enqueue(queue, command, args, maxRetries, runAt);
  }

  public List<JobDto> list(Optional<JobState> state, Optional<String> queue, int limit) {
    return jobService.list(state, queue, limit);
  }

  public Optional<JobDto> inspect(UUID jobId) {
    return jobService.inspect(jobId);
  }

  public boolean cancel(UUID jobId) {
    return jobService.cancel(jobId);
  }
}

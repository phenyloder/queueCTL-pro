package com.queuectl.application.service;

import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobService {

  JobDto enqueue(String queue, String command, List<String> args, Integer maxRetries, Instant runAt);

  List<JobDto> list(Optional<JobState> state, Optional<String> queue, int limit);

  Optional<JobDto> inspect(UUID jobId);

  boolean cancel(UUID jobId);
}

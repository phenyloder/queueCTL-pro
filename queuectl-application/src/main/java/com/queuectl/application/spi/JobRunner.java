package com.queuectl.application.spi;

import com.queuectl.application.model.ExecutionResult;
import com.queuectl.domain.JobDto;
import java.time.Duration;
import java.util.Set;

public interface JobRunner {
  ExecutionResult run(
      JobDto job, Duration timeout, Set<String> allowedCommands, long maxOutputBytes);
}

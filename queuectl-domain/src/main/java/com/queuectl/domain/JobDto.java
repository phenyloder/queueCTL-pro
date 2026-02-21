package com.queuectl.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobDto(
    UUID id,
    String queue,
    String command,
    List<String> args,
    JobState state,
    int attempts,
    int maxRetries,
    Instant runAt,
    UUID leaseId,
    Instant leaseExpiresAt,
    Instant createdAt,
    Instant updatedAt,
    String lastError,
    String outputRef) {

  public boolean isTerminal() {
    return state == JobState.COMPLETED || state == JobState.DEAD || state == JobState.CANCELED;
  }
}

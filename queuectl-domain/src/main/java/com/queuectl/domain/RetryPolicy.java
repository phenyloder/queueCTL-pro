package com.queuectl.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class RetryPolicy {

  private RetryPolicy() {}

  public static Duration nextDelay(
      int attempt, int base, int maxDelaySeconds, JitterType jitterType, ThreadLocalRandom random) {
    Objects.requireNonNull(jitterType, "jitterType");
    if (attempt < 1) {
      throw new IllegalArgumentException("attempt must be >= 1");
    }
    if (base < 1) {
      throw new IllegalArgumentException("base must be >= 1");
    }
    if (maxDelaySeconds < 1) {
      throw new IllegalArgumentException("maxDelaySeconds must be >= 1");
    }

    long rawDelay = (long) Math.pow(base, attempt);
    long cappedDelay = Math.min(rawDelay, maxDelaySeconds);

    if (jitterType == JitterType.FULL) {
      long jitter = random.nextLong(cappedDelay + 1);
      return Duration.ofSeconds(jitter);
    }

    return Duration.ofSeconds(cappedDelay);
  }
}

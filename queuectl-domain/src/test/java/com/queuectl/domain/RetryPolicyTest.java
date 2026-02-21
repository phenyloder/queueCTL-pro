package com.queuectl.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void shouldCapDelay() {
    Duration delay = RetryPolicy.nextDelay(8, 2, 300, JitterType.FULL, ThreadLocalRandom.current());
    assertTrue(delay.getSeconds() <= 300);
  }

  @Test
  void shouldProduceNonNegativeDelay() {
    Duration delay = RetryPolicy.nextDelay(1, 2, 300, JitterType.FULL, ThreadLocalRandom.current());
    assertTrue(delay.getSeconds() >= 0);
  }

  @Test
  void shouldAllowBaseOne() {
    Duration delay = RetryPolicy.nextDelay(3, 1, 10, JitterType.FULL, ThreadLocalRandom.current());
    assertTrue(delay.getSeconds() <= 1);
  }
}

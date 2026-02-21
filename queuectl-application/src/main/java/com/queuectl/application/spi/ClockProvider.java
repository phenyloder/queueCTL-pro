package com.queuectl.application.spi;

import java.time.Instant;

public interface ClockProvider {
  Instant now();
}

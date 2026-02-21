package com.queuectl.infra.config;

import com.queuectl.application.spi.ClockProvider;
import java.time.Instant;

public final class SystemClockProvider implements ClockProvider {

  @Override
  public Instant now() {
    return Instant.now();
  }
}

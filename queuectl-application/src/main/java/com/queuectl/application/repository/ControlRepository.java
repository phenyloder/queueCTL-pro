package com.queuectl.application.repository;

import java.time.Instant;

public interface ControlRepository {

  void setShutdownRequested(boolean shutdownRequested, Instant now);

  boolean isShutdownRequested();
}

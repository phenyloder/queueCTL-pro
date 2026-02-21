package com.queuectl.infra.metrics;

import com.queuectl.application.spi.QueueMetrics;
import com.queuectl.domain.JobState;
import java.util.Map;

public final class NoopQueueMetrics implements QueueMetrics {

  @Override
  public void onEnqueued(String queue) {}

  @Override
  public void onLeased(String queue) {}

  @Override
  public void onCompleted(String queue) {}

  @Override
  public void onFailed(String queue) {}

  @Override
  public void onDead(String queue) {}

  @Override
  public void refreshGauges(Map<JobState, Long> countsByState, long dlqSize, int activeWorkers) {}
}

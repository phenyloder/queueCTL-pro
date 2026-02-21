package com.queuectl.application.spi;

import com.queuectl.domain.JobState;
import java.util.Map;

public interface QueueMetrics {
  void onEnqueued(String queue);

  void onLeased(String queue);

  void onCompleted(String queue);

  void onFailed(String queue);

  void onDead(String queue);

  void refreshGauges(Map<JobState, Long> countsByState, long dlqSize, int activeWorkers);
}

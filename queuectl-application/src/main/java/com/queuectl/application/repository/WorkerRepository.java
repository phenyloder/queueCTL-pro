package com.queuectl.application.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkerRepository {

  int countActiveWorkers(Duration activeWithin, Instant now);

  void upsertWorkerHeartbeat(UUID workerId, List<String> queues, Instant now);

  void removeWorkerHeartbeat(UUID workerId);
}

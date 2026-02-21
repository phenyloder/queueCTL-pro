package com.queuectl.application.service.impl;

import com.queuectl.application.model.StatusSnapshot;
import com.queuectl.application.repository.DlqRepository;
import com.queuectl.application.repository.JobRepository;
import com.queuectl.application.repository.WorkerRepository;
import com.queuectl.application.service.StatusService;
import com.queuectl.application.spi.ClockProvider;
import java.time.Duration;
import java.time.Instant;

public final class StatusServiceImpl implements StatusService {

  private final JobRepository jobRepository;
  private final DlqRepository dlqRepository;
  private final WorkerRepository workerRepository;
  private final ClockProvider clockProvider;

  public StatusServiceImpl(
      JobRepository jobRepository,
      DlqRepository dlqRepository,
      WorkerRepository workerRepository,
      ClockProvider clockProvider) {
    this.jobRepository = jobRepository;
    this.dlqRepository = dlqRepository;
    this.workerRepository = workerRepository;
    this.clockProvider = clockProvider;
  }

  @Override
  public StatusSnapshot getSnapshot() {
    Instant now = clockProvider.now();
    int activeWorkers = workerRepository.countActiveWorkers(Duration.ofSeconds(15), now);
    return new StatusSnapshot(
        jobRepository.countByState(), activeWorkers, dlqRepository.countDlq());
  }
}

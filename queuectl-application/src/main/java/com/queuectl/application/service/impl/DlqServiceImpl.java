package com.queuectl.application.service.impl;

import com.queuectl.application.repository.DlqRepository;
import com.queuectl.application.service.DlqService;
import com.queuectl.application.spi.ClockProvider;
import com.queuectl.domain.DlqEntryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DlqServiceImpl implements DlqService {

  private final DlqRepository dlqRepository;
  private final ClockProvider clockProvider;

  public DlqServiceImpl(DlqRepository dlqRepository, ClockProvider clockProvider) {
    this.dlqRepository = dlqRepository;
    this.clockProvider = clockProvider;
  }

  @Override
  public List<DlqEntryDto> list(int limit) {
    return dlqRepository.listDlq(limit);
  }

  @Override
  public boolean retry(UUID jobId, boolean resetAttempts) {
    Instant now = clockProvider.now();
    return dlqRepository.retryDlqJob(jobId, resetAttempts, now);
  }
}

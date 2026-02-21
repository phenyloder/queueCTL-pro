package com.queuectl.application.service.impl;

import com.queuectl.application.repository.ControlRepository;
import com.queuectl.application.service.WorkerControlService;
import com.queuectl.application.spi.ClockProvider;

public final class WorkerControlServiceImpl implements WorkerControlService {

  private final ControlRepository controlRepository;
  private final ClockProvider clockProvider;

  public WorkerControlServiceImpl(
      ControlRepository controlRepository, ClockProvider clockProvider) {
    this.controlRepository = controlRepository;
    this.clockProvider = clockProvider;
  }

  @Override
  public void requestStop() {
    controlRepository.setShutdownRequested(true, clockProvider.now());
  }

  @Override
  public void clearStopSignal() {
    controlRepository.setShutdownRequested(false, clockProvider.now());
  }
}

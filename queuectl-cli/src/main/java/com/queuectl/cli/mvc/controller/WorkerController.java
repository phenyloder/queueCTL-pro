package com.queuectl.cli.mvc.controller;

import com.queuectl.application.model.WorkerOptions;
import com.queuectl.application.service.WorkerControlService;
import com.queuectl.application.service.WorkerPoolService;

public final class WorkerController {

  private final WorkerPoolService workerPoolService;
  private final WorkerControlService workerControlService;

  public WorkerController(
      WorkerPoolService workerPoolService, WorkerControlService workerControlService) {
    this.workerPoolService = workerPoolService;
    this.workerControlService = workerControlService;
  }

  public void start(WorkerOptions options) {
    workerPoolService.runWorkers(options);
  }

  public void requestStop() {
    workerControlService.requestStop();
  }
}

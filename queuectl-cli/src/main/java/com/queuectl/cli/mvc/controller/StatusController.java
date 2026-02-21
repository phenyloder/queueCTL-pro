package com.queuectl.cli.mvc.controller;

import com.queuectl.application.model.StatusSnapshot;
import com.queuectl.application.service.StatusService;

public final class StatusController {

  private final StatusService statusService;

  public StatusController(StatusService statusService) {
    this.statusService = statusService;
  }

  public StatusSnapshot getSnapshot() {
    return statusService.getSnapshot();
  }
}

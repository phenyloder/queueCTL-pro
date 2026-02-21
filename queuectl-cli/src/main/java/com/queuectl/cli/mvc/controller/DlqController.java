package com.queuectl.cli.mvc.controller;

import com.queuectl.application.service.DlqService;
import com.queuectl.domain.DlqEntryDto;
import java.util.List;
import java.util.UUID;

public final class DlqController {

  private final DlqService dlqService;

  public DlqController(DlqService dlqService) {
    this.dlqService = dlqService;
  }

  public List<DlqEntryDto> list(int limit) {
    return dlqService.list(limit);
  }

  public boolean retry(UUID jobId, boolean resetAttempts) {
    return dlqService.retry(jobId, resetAttempts);
  }
}

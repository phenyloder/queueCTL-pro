package com.queuectl.cli.mvc.controller;

import com.queuectl.application.model.StatusSnapshot;
import com.queuectl.application.service.JobService;
import com.queuectl.application.service.StatusService;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.util.List;
import java.util.Optional;

public final class DashboardController {

  private final StatusService statusService;
  private final JobService jobService;

  public DashboardController(StatusService statusService, JobService jobService) {
    this.statusService = statusService;
    this.jobService = jobService;
  }

  public StatusSnapshot status() {
    return statusService.getSnapshot();
  }

  public List<JobDto> jobs(Optional<JobState> state, Optional<String> queue, int limit) {
    return jobService.list(state, queue, limit);
  }
}

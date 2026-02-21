package com.queuectl.cli.bootstrap;

import com.queuectl.cli.mvc.controller.ConfigController;
import com.queuectl.cli.mvc.controller.DashboardController;
import com.queuectl.cli.mvc.controller.DlqController;
import com.queuectl.cli.mvc.controller.JobController;
import com.queuectl.cli.mvc.controller.StatusController;
import com.queuectl.cli.mvc.controller.WorkerController;
import java.nio.file.Path;

public final class QueueCTLFacade implements AutoCloseable {

  private final QueueCtlContext context;
  private final JobController jobController;
  private final WorkerController workerController;
  private final StatusController statusController;
  private final DlqController dlqController;
  private final ConfigController configController;
  private final DashboardController dashboardController;

  private QueueCTLFacade(QueueCtlContext context) {
    this.context = context;
    this.jobController = new JobController(context.jobService());
    this.workerController =
        new WorkerController(context.workerPoolService(), context.workerControlService());
    this.statusController = new StatusController(context.statusService());
    this.dlqController = new DlqController(context.dlqService());
    this.configController = new ConfigController(context.configService());
    this.dashboardController =
        new DashboardController(context.statusService(), context.jobService());
  }

  public static QueueCTLFacade create(boolean enableMetrics, Path outputDirectory) {
    return new QueueCTLFacade(QueueCtlContext.create(enableMetrics, outputDirectory));
  }

  public JobController jobController() {
    return jobController;
  }

  public WorkerController workerController() {
    return workerController;
  }

  public StatusController statusController() {
    return statusController;
  }

  public DlqController dlqController() {
    return dlqController;
  }

  public ConfigController configController() {
    return configController;
  }

  public DashboardController dashboardController() {
    return dashboardController;
  }

  public void startMetricsServer(int port) {
    context.startMetricsServer(port);
  }

  @Override
  public void close() {
    context.close();
  }
}

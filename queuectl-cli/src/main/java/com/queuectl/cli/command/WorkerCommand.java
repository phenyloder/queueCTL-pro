package com.queuectl.cli.command;

import com.queuectl.application.model.WorkerOptions;
import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.WorkerController;
import com.queuectl.cli.mvc.view.ConsoleView;
import com.queuectl.cli.support.DurationParser;
import java.time.Duration;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "worker",
    description = "Worker controls",
    subcommands = {WorkerCommand.Start.class, WorkerCommand.Stop.class})
public final class WorkerCommand implements Runnable {

  @Override
  public void run() {
    new ConsoleView(System.out).showUsageHint("Use worker start or worker stop");
  }

  @Command(name = "start", description = "Start worker pool")
  static final class Start extends ContextCommandSupport implements Runnable {

    @Option(names = "--count", defaultValue = "1")
    private int count;

    @Option(names = "--queues", split = ",", defaultValue = "default")
    private List<String> queues;

    @Option(names = "--lease-ttl", defaultValue = "30s")
    private String leaseTtl;

    @Option(names = "--heartbeat", defaultValue = "5s")
    private String heartbeat;

    @Option(names = "--poll", defaultValue = "250ms")
    private String poll;

    @Option(names = "--metrics-port", defaultValue = "9090")
    private int metricsPort;

    @Override
    public void run() {
      Duration leaseDuration = DurationParser.parse(leaseTtl);
      Duration heartbeatDuration = DurationParser.parse(heartbeat);
      Duration pollDuration = DurationParser.parse(poll);

      withModule(
          true,
          module -> {
            WorkerController controller = module.workerController();
            module.startMetricsServer(metricsPort);
            WorkerOptions options =
                new WorkerOptions(
                    count, queues, leaseDuration, heartbeatDuration, pollDuration, metricsPort);
            controller.start(options);
          });
    }
  }

  @Command(name = "stop", description = "Request graceful worker shutdown")
  static final class Stop extends ContextCommandSupport implements Runnable {

    @Override
    public void run() {
      withModule(
          false,
          module -> {
            WorkerController controller = module.workerController();
            ConsoleView view = new ConsoleView(System.out);
            controller.requestStop();
            view.showShutdownRequested();
          });
    }
  }
}

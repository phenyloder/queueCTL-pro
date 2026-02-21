package com.queuectl.cli.command;

import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.JobController;
import com.queuectl.cli.mvc.view.ConsoleView;
import com.queuectl.domain.JobDto;
import java.util.UUID;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "job",
    description = "Job operations",
    subcommands = {JobCommand.Inspect.class, JobCommand.Cancel.class})
public final class JobCommand implements Runnable {

  @Override
  public void run() {
    new ConsoleView(System.out).showUsageHint("Use job inspect/cancel");
  }

  @Command(name = "inspect", description = "Inspect job details")
  static final class Inspect extends ContextCommandSupport implements Runnable {

    @Parameters(index = "0", paramLabel = "jobId")
    private UUID jobId;

    @Override
    public void run() {
      withModule(
          false,
          module -> {
            JobController controller = module.jobController();
            ConsoleView view = new ConsoleView(System.out);
            JobDto job = controller.inspect(jobId).orElse(null);
            if (job == null) {
              view.showJobNotFound(jobId);
              return;
            }
            view.showJobDetails(job);
          });
    }
  }

  @Command(name = "cancel", description = "Cancel a job")
  static final class Cancel extends ContextCommandSupport implements Runnable {

    @Parameters(index = "0", paramLabel = "jobId")
    private UUID jobId;

    @Override
    public void run() {
      withModule(
          false,
          module -> {
            JobController controller = module.jobController();
            ConsoleView view = new ConsoleView(System.out);
            boolean canceled = controller.cancel(jobId);
            view.showCancelResult(jobId, canceled);
          });
    }
  }
}

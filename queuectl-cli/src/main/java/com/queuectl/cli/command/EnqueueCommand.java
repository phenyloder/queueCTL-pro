package com.queuectl.cli.command;

import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.JobController;
import com.queuectl.cli.mvc.view.ConsoleView;
import com.queuectl.domain.JobDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "enqueue", description = "Enqueue a job")
public final class EnqueueCommand extends ContextCommandSupport implements Runnable {

  @Option(names = "--queue", defaultValue = "default", description = "Queue name")
  private String queue;

  @Option(names = "--command", required = true, description = "Command to execute")
  private String command;

  @Option(names = "--arg", description = "Argument (repeatable)")
  private List<String> args = new ArrayList<>();

  @Option(names = "--max-retries", description = "Maximum retries")
  private Integer maxRetries;

  @Option(names = "--run-at", description = "Scheduled run time in ISO-8601 UTC")
  private Instant runAt;

  @Override
  public void run() {
    withModule(
        false,
        module -> {
          JobController controller = module.jobController();
          ConsoleView view = new ConsoleView(System.out);
          JobDto job = controller.enqueue(queue, command, args, maxRetries, runAt);
          view.showEnqueued(job);
        });
  }
}

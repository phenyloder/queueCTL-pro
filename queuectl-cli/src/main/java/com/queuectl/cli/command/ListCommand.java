package com.queuectl.cli.command;

import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.JobController;
import com.queuectl.cli.mvc.view.ConsoleView;
import com.queuectl.cli.support.JobStateParser;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "list", description = "List jobs")
public final class ListCommand extends ContextCommandSupport implements Runnable {

  @Option(names = "--state", description = "Filter state")
  private String state;

  @Option(names = "--queue", description = "Filter queue")
  private String queue;

  @Option(names = "--limit", defaultValue = "50")
  private int limit;

  @Override
  public void run() {
    Optional<JobState> stateFilter = Optional.ofNullable(state).map(JobStateParser::parse);
    Optional<String> queueFilter = Optional.ofNullable(queue);

    withModule(
        false,
        module -> {
          JobController controller = module.jobController();
          ConsoleView view = new ConsoleView(System.out);
          List<JobDto> jobs = controller.list(stateFilter, queueFilter, limit);
          view.showJobs(jobs);
        });
  }
}

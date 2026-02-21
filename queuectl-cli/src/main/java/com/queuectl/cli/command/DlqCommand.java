package com.queuectl.cli.command;

import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.DlqController;
import com.queuectl.cli.mvc.view.ConsoleView;
import com.queuectl.domain.DlqEntryDto;
import java.util.List;
import java.util.UUID;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "dlq",
    description = "DLQ operations",
    subcommands = {DlqCommand.ListDlq.class, DlqCommand.RetryDlq.class})
public final class DlqCommand implements Runnable {

  @Override
  public void run() {
    new ConsoleView(System.out).showUsageHint("Use dlq list or dlq retry");
  }

  @Command(name = "list", description = "List DLQ entries")
  static final class ListDlq extends ContextCommandSupport implements Runnable {

    @Option(names = "--limit", defaultValue = "50")
    private int limit;

    @Override
    public void run() {
      withModule(
          false,
          module -> {
            DlqController controller = module.dlqController();
            ConsoleView view = new ConsoleView(System.out);
            List<DlqEntryDto> entries = controller.list(limit);
            view.showDlq(entries);
          });
    }
  }

  @Command(name = "retry", description = "Move dead job back to pending")
  static final class RetryDlq extends ContextCommandSupport implements Runnable {

    @Parameters(index = "0", paramLabel = "jobId")
    private UUID jobId;

    @Option(names = "--reset-attempts", defaultValue = "true")
    private boolean resetAttempts;

    @Override
    public void run() {
      withModule(
          false,
          module -> {
            DlqController controller = module.dlqController();
            ConsoleView view = new ConsoleView(System.out);
            boolean retried = controller.retry(jobId, resetAttempts);
            view.showDlqRetryResult(jobId, retried);
          });
    }
  }
}

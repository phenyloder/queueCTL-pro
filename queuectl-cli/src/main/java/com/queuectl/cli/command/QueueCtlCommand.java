package com.queuectl.cli.command;

import com.queuectl.cli.mvc.view.ConsoleView;
import picocli.CommandLine.Command;

@Command(
    name = "queuectl",
    mixinStandardHelpOptions = true,
    description = "queuectl background job queue",
    subcommands = {
      EnqueueCommand.class,
      WorkerCommand.class,
      StatusCommand.class,
      ListCommand.class,
      DlqCommand.class,
      ConfigCommand.class,
      JobCommand.class,
      UiCommand.class
    })
public final class QueueCtlCommand implements Runnable {

  @Override
  public void run() {
    new ConsoleView(System.out).showUsageHint("Use --help to see available commands.");
  }
}

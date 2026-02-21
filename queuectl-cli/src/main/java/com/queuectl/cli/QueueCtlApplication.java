package com.queuectl.cli;

import com.queuectl.cli.command.QueueCtlCommand;
import picocli.CommandLine;

public final class QueueCtlApplication {

  private QueueCtlApplication() {}

  public static void main(String[] args) {
    int exitCode = new CommandLine(new QueueCtlCommand()).execute(args);
    System.exit(exitCode);
  }
}

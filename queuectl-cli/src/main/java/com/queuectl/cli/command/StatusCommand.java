package com.queuectl.cli.command;

import com.queuectl.application.model.StatusSnapshot;
import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.StatusController;
import com.queuectl.cli.mvc.view.ConsoleView;
import picocli.CommandLine.Command;

@Command(name = "status", description = "Queue status summary")
public final class StatusCommand extends ContextCommandSupport implements Runnable {

  @Override
  public void run() {
    withModule(
        false,
        module -> {
          StatusController controller = module.statusController();
          ConsoleView view = new ConsoleView(System.out);
          StatusSnapshot snapshot = controller.getSnapshot();
          view.showStatus(snapshot);
        });
  }
}

package com.queuectl.cli.command;

import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.ConfigController;
import com.queuectl.cli.mvc.view.ConsoleView;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "config",
    description = "Config management",
    subcommands = {ConfigCommand.SetConfig.class, ConfigCommand.GetConfig.class})
public final class ConfigCommand implements Runnable {

  @Override
  public void run() {
    new ConsoleView(System.out).showUsageHint("Use config set/get");
  }

  @Command(name = "set", description = "Set a config value")
  static final class SetConfig extends ContextCommandSupport implements Runnable {

    @Parameters(index = "0")
    private String key;

    @Parameters(index = "1")
    private String value;

    @Override
    public void run() {
      withModule(
          false,
          module -> {
            ConfigController controller = module.configController();
            ConsoleView view = new ConsoleView(System.out);
            controller.set(key, value);
            view.showConfigSet(key, value);
          });
    }
  }

  @Command(name = "get", description = "Get a config value")
  static final class GetConfig extends ContextCommandSupport implements Runnable {

    @Parameters(index = "0")
    private String key;

    @Override
    public void run() {
      withModule(
          false,
          module -> {
            ConfigController controller = module.configController();
            ConsoleView view = new ConsoleView(System.out);
            String value = controller.get(key).orElse("<unset>");
            view.showConfigValue(key, value);
          });
    }
  }
}

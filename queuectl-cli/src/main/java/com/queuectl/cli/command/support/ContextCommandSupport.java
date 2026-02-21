package com.queuectl.cli.command.support;

import com.queuectl.cli.bootstrap.QueueCTLFacade;
import java.nio.file.Path;
import java.util.function.Consumer;

public abstract class ContextCommandSupport {

  private static final Path OUTPUT_DIRECTORY = Path.of("data/outputs");

  protected final QueueCTLFacade createModule(boolean enableMetrics) {
    return QueueCTLFacade.create(enableMetrics, OUTPUT_DIRECTORY);
  }

  protected final void withModule(boolean enableMetrics, Consumer<QueueCTLFacade> action) {
    try (QueueCTLFacade module = createModule(enableMetrics)) {
      action.accept(module);
    }
  }
}

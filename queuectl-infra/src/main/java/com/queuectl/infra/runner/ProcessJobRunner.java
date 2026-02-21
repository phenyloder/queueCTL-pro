package com.queuectl.infra.runner;

import com.queuectl.application.model.ExecutionResult;
import com.queuectl.application.spi.JobRunner;
import com.queuectl.domain.JobDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessJobRunner implements JobRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessJobRunner.class);

  private final Path outputDirectory;

  public ProcessJobRunner(Path outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  @Override
  public ExecutionResult run(
      JobDto job, Duration timeout, Set<String> allowedCommands, long maxOutputBytes) {
    String outputRef = prepareOutputPath(job).toAbsolutePath().toString();
    String commandName = normalizeCommandName(job.command());

    if (!allowedCommands.contains(commandName)
        && !allowedCommands.contains(job.command().toLowerCase(Locale.ROOT))) {
      String reason = "Command is not in allowlist: " + job.command();
      writeFallbackOutput(outputRef, reason);
      return new ExecutionResult(-1, false, outputRef, reason, null);
    }

    List<String> command = new ArrayList<>();
    command.add(job.command());
    command.addAll(job.args());

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.redirectErrorStream(true);

    Process process;
    try {
      process = processBuilder.start();
    } catch (IOException ioException) {
      String reason = "Failed to start process: " + ioException.getMessage();
      writeFallbackOutput(outputRef, reason);
      return new ExecutionResult(-1, false, outputRef, reason, ioException);
    }

    AtomicReference<Throwable> streamError = new AtomicReference<>();
    Path outputPath = Path.of(outputRef);
    Thread streamThread =
        Thread.startVirtualThread(
            () -> {
              try (InputStream inputStream = process.getInputStream();
                  OutputStream outputStream = Files.newOutputStream(outputPath)) {
                copyBounded(inputStream, outputStream, maxOutputBytes);
              } catch (Throwable throwable) {
                streamError.set(throwable);
              }
            });

    boolean timedOut = false;
    int exitCode;
    try {
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        timedOut = true;
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor(2, TimeUnit.SECONDS);
        }
      }
      exitCode = timedOut ? -1 : process.exitValue();
      streamThread.join();
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return new ExecutionResult(
          -1, false, outputRef, "Execution interrupted", interruptedException);
    }

    Throwable outputError = streamError.get();
    if (outputError != null) {
      LOGGER.warn(
          "Failed to capture process output for job {}: {}", job.id(), outputError.getMessage());
    }

    if (timedOut) {
      return new ExecutionResult(exitCode, true, outputRef, "Job timed out", null);
    }
    if (exitCode != 0) {
      return new ExecutionResult(
          exitCode, false, outputRef, "Process exited with code " + exitCode, null);
    }
    return new ExecutionResult(exitCode, false, outputRef, null, null);
  }

  private Path prepareOutputPath(JobDto job) {
    try {
      Files.createDirectories(outputDirectory);
    } catch (IOException ioException) {
      throw new IllegalStateException(
          "Unable to create output directory " + outputDirectory, ioException);
    }
    return outputDirectory.resolve(job.id() + ".log");
  }

  private static String normalizeCommandName(String command) {
    String normalized = command.trim();
    int slash = normalized.lastIndexOf('/');
    if (slash >= 0 && slash < normalized.length() - 1) {
      normalized = normalized.substring(slash + 1);
    }
    return normalized.toLowerCase(Locale.ROOT);
  }

  private void writeFallbackOutput(String outputRef, String message) {
    try {
      Files.createDirectories(outputDirectory);
      Files.writeString(Path.of(outputRef), message + System.lineSeparator());
    } catch (IOException ioException) {
      LOGGER.warn("Unable to write fallback output file {}", outputRef, ioException);
    }
  }

  private static void copyBounded(
      InputStream inputStream, OutputStream outputStream, long maxOutputBytes) throws IOException {
    byte[] buffer = new byte[8192];
    long written = 0;
    int read;
    while ((read = inputStream.read(buffer)) >= 0) {
      if (written < maxOutputBytes) {
        int bytesToWrite = (int) Math.min(read, maxOutputBytes - written);
        if (bytesToWrite > 0) {
          outputStream.write(buffer, 0, bytesToWrite);
          written += bytesToWrite;
        }
      }
    }
  }
}

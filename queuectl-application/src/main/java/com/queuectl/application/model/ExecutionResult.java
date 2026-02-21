package com.queuectl.application.model;

public record ExecutionResult(
    int exitCode, boolean timedOut, String outputRef, String failureReason, Throwable error) {

  public boolean isSuccess() {
    return exitCode == 0 && !timedOut && failureReason == null && error == null;
  }

  public String describeFailure() {
    if (error != null) {
      return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    if (timedOut) {
      return "Job timed out";
    }
    if (failureReason != null && !failureReason.isBlank()) {
      return failureReason;
    }
    return "Process exited with code " + exitCode;
  }
}

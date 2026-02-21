package com.queuectl.application.config;

public final class ConfigKeys {

  public static final String MAX_RETRIES = "max-retries";
  public static final String BACKOFF_BASE = "backoff-base";
  public static final String MAX_DELAY_SECONDS = "max-delay-seconds";
  public static final String ALLOWED_COMMANDS = "allowed-commands";
  public static final String JOB_TIMEOUT_SECONDS = "job-timeout-seconds";
  public static final String MAX_OUTPUT_BYTES = "max-output-bytes";

  private ConfigKeys() {}
}

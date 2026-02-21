package com.queuectl.application.service.impl;

import com.queuectl.application.config.ConfigKeys;
import com.queuectl.application.model.QueueConfig;
import com.queuectl.application.repository.ConfigRepository;
import com.queuectl.application.service.QueueConfigProvider;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class QueueConfigProviderImpl implements QueueConfigProvider {

  private static final int DEFAULT_MAX_RETRIES = 3;
  private static final int DEFAULT_BACKOFF_BASE = 2;
  private static final int DEFAULT_MAX_DELAY_SECONDS = 300;
  private static final int DEFAULT_JOB_TIMEOUT_SECONDS = 30;
  private static final long DEFAULT_MAX_OUTPUT_BYTES = 1_000_000L;
  private static final Set<String> DEFAULT_ALLOWED_COMMANDS = Set.of("echo", "sleep");

  private final ConfigRepository configRepository;

  public QueueConfigProviderImpl(ConfigRepository configRepository) {
    this.configRepository = configRepository;
  }

  @Override
  public QueueConfig load(int fallbackJobTimeoutSeconds) {
    int jobTimeout = intConfig(ConfigKeys.JOB_TIMEOUT_SECONDS, fallbackJobTimeoutSeconds);
    if (jobTimeout <= 0) {
      jobTimeout = DEFAULT_JOB_TIMEOUT_SECONDS;
    }
    return new QueueConfig(
        intConfig(ConfigKeys.MAX_RETRIES, DEFAULT_MAX_RETRIES),
        intConfig(ConfigKeys.BACKOFF_BASE, DEFAULT_BACKOFF_BASE),
        intConfig(ConfigKeys.MAX_DELAY_SECONDS, DEFAULT_MAX_DELAY_SECONDS),
        jobTimeout,
        longConfig(ConfigKeys.MAX_OUTPUT_BYTES, DEFAULT_MAX_OUTPUT_BYTES),
        setConfig(ConfigKeys.ALLOWED_COMMANDS, DEFAULT_ALLOWED_COMMANDS));
  }

  private int intConfig(String key, int defaultValue) {
    return configRepository
        .getConfig(key)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> Integer.parseInt(value, 10))
        .orElse(defaultValue);
  }

  private long longConfig(String key, long defaultValue) {
    return configRepository
        .getConfig(key)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> Long.parseLong(value, 10))
        .orElse(defaultValue);
  }

  private Set<String> setConfig(String key, Set<String> defaultValue) {
    return configRepository
        .getConfig(key)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(
            value ->
                Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet()))
        .orElse(defaultValue);
  }
}

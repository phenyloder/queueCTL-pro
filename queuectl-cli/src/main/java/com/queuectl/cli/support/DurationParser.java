package com.queuectl.cli.support;

import java.time.Duration;

public final class DurationParser {

  private DurationParser() {}

  public static Duration parse(String value) {
    String trimmed = value.trim().toLowerCase();
    if (trimmed.endsWith("ms")) {
      return Duration.ofMillis(Long.parseLong(trimmed.substring(0, trimmed.length() - 2), 10));
    }
    if (trimmed.endsWith("s")) {
      return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1), 10));
    }
    if (trimmed.endsWith("m")) {
      return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1), 10));
    }
    if (trimmed.endsWith("h")) {
      return Duration.ofHours(Long.parseLong(trimmed.substring(0, trimmed.length() - 1), 10));
    }
    return Duration.parse(value);
  }
}

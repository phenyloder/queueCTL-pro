package com.queuectl.cli.support;

import com.queuectl.domain.JobState;
import java.util.Locale;

public final class JobStateParser {

  private JobStateParser() {}

  public static JobState parse(String value) {
    return JobState.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}

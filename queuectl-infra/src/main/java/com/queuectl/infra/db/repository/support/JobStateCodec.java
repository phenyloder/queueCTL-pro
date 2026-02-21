package com.queuectl.infra.db.repository.support;

import com.queuectl.domain.JobState;
import java.util.Locale;

public final class JobStateCodec {

  private JobStateCodec() {}

  public static String toDbState(JobState state) {
    return state.name().toLowerCase(Locale.ROOT);
  }

  public static JobState fromDbState(String state) {
    return JobState.valueOf(state.toUpperCase(Locale.ROOT));
  }
}

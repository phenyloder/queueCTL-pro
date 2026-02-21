package com.queuectl.cli.mvc.view;

import com.queuectl.application.model.StatusSnapshot;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DashboardJsonView {

  public String renderStatus(StatusSnapshot status) {
    Map<JobState, Long> counts = new EnumMap<>(JobState.class);
    for (JobState state : JobState.values()) {
      counts.put(state, status.countsByState().getOrDefault(state, 0L));
    }

    return "{"
        + "\"activeWorkers\":"
        + status.activeWorkers()
        + ","
        + "\"dlqSize\":"
        + status.dlqSize()
        + ","
        + "\"counts\":"
        + "{"
        + "\"pending\":"
        + counts.get(JobState.PENDING)
        + ","
        + "\"leased\":"
        + counts.get(JobState.LEASED)
        + ","
        + "\"processing\":"
        + counts.get(JobState.PROCESSING)
        + ","
        + "\"completed\":"
        + counts.get(JobState.COMPLETED)
        + ","
        + "\"failed\":"
        + counts.get(JobState.FAILED)
        + ","
        + "\"dead\":"
        + counts.get(JobState.DEAD)
        + ","
        + "\"canceled\":"
        + counts.get(JobState.CANCELED)
        + "}"
        + "}";
  }

  public String renderJobs(List<JobDto> jobs) {
    StringBuilder builder = new StringBuilder();
    builder.append('{').append("\"jobs\":[");
    for (int i = 0; i < jobs.size(); i++) {
      JobDto job = jobs.get(i);
      if (i > 0) {
        builder.append(',');
      }
      builder
          .append('{')
          .append("\"id\":\"")
          .append(json(job.id().toString()))
          .append("\",")
          .append("\"queue\":\"")
          .append(json(job.queue()))
          .append("\",")
          .append("\"command\":\"")
          .append(json(job.command()))
          .append("\",")
          .append("\"state\":\"")
          .append(json(job.state().name().toLowerCase(Locale.ROOT)))
          .append("\",")
          .append("\"attempts\":")
          .append(job.attempts())
          .append(',')
          .append("\"maxRetries\":")
          .append(job.maxRetries())
          .append(',')
          .append("\"runAt\":\"")
          .append(json(stringOf(job.runAt())))
          .append("\",")
          .append("\"lastError\":\"")
          .append(json(job.lastError() == null ? "" : job.lastError()))
          .append("\",")
          .append("\"args\":[");

      List<String> args = job.args();
      for (int j = 0; j < args.size(); j++) {
        if (j > 0) {
          builder.append(',');
        }
        builder.append('"').append(json(args.get(j))).append('"');
      }
      builder.append("]}");
    }
    builder.append("]}");
    return builder.toString();
  }

  private static String stringOf(Instant instant) {
    return instant == null ? "" : instant.toString();
  }

  private static String json(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}

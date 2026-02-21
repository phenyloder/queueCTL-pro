package com.queuectl.cli.mvc.view;

import com.queuectl.application.model.StatusSnapshot;
import com.queuectl.domain.DlqEntryDto;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import java.io.PrintStream;
import java.util.List;
import java.util.UUID;

public final class ConsoleView {

  private final PrintStream output;

  public ConsoleView(PrintStream output) {
    this.output = output;
  }

  public void showEnqueued(JobDto job) {
    output.printf("Enqueued job %s in queue %s (state=%s)%n", job.id(), job.queue(), job.state());
  }

  public void showShutdownRequested() {
    output.println("Shutdown requested. Workers will stop gracefully.");
  }

  public void showStatus(StatusSnapshot snapshot) {
    output.printf("Active workers: %d%n", snapshot.activeWorkers());
    for (JobState state : JobState.values()) {
      long count = snapshot.countsByState().getOrDefault(state, 0L);
      output.printf("%-10s %d%n", state.name().toLowerCase(), count);
    }
    output.printf("dlq: %d%n", snapshot.dlqSize());
  }

  public void showJobs(List<JobDto> jobs) {
    if (jobs.isEmpty()) {
      output.println("No jobs found.");
      return;
    }

    output.println("id | queue | state | attempts/max | run_at | command");
    for (JobDto job : jobs) {
      output.printf(
          "%s | %s | %s | %d/%d | %s | %s %s%n",
          job.id(),
          job.queue(),
          job.state().name().toLowerCase(),
          job.attempts(),
          job.maxRetries(),
          job.runAt(),
          job.command(),
          String.join(" ", job.args()));
    }
  }

  public void showDlq(List<DlqEntryDto> entries) {
    if (entries.isEmpty()) {
      output.println("DLQ is empty.");
      return;
    }

    output.println("id | job_id | moved_at | reason");
    for (DlqEntryDto entry : entries) {
      output.printf(
          "%s | %s | %s | %s%n", entry.id(), entry.jobId(), entry.movedAt(), entry.reason());
    }
  }

  public void showDlqRetryResult(UUID jobId, boolean retried) {
    if (retried) {
      output.printf("Retried dead job %s%n", jobId);
    } else {
      output.printf("Job %s was not retried (not found or not dead).%n", jobId);
    }
  }

  public void showConfigSet(String key, String value) {
    output.printf("Set %s=%s%n", key, value);
  }

  public void showConfigValue(String key, String value) {
    output.printf("%s=%s%n", key, value);
  }

  public void showJobDetails(JobDto job) {
    output.printf("id=%s%n", job.id());
    output.printf("queue=%s%n", job.queue());
    output.printf("command=%s%n", job.command());
    output.printf("args=%s%n", job.args());
    output.printf("state=%s%n", job.state().name().toLowerCase());
    output.printf("attempts=%d%n", job.attempts());
    output.printf("max_retries=%d%n", job.maxRetries());
    output.printf("run_at=%s%n", job.runAt());
    output.printf("lease_id=%s%n", job.leaseId());
    output.printf("lease_expires_at=%s%n", job.leaseExpiresAt());
    output.printf("created_at=%s%n", job.createdAt());
    output.printf("updated_at=%s%n", job.updatedAt());
    output.printf("last_error=%s%n", job.lastError());
    output.printf("output_ref=%s%n", job.outputRef());
  }

  public void showJobNotFound(UUID jobId) {
    output.printf("Job %s not found%n", jobId);
  }

  public void showCancelResult(UUID jobId, boolean canceled) {
    if (canceled) {
      output.printf("Canceled job %s%n", jobId);
    } else {
      output.printf("Job %s was not canceled (not found or already terminal).%n", jobId);
    }
  }

  public void showUsageHint(String message) {
    output.println(message);
  }
}

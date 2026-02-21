package com.queuectl.infra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dlq")
public class DlqEntry {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "moved_at", nullable = false)
  private Instant movedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getMovedAt() {
    return movedAt;
  }

  public void setMovedAt(Instant movedAt) {
    this.movedAt = movedAt;
  }
}

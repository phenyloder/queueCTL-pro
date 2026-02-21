package com.queuectl.infra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
public class Job {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "queue", nullable = false)
  private String queueName;

  @Column(name = "command", nullable = false)
  private String command;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "args", nullable = false, columnDefinition = "text[]")
  private String[] args = new String[0];

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "max_retries", nullable = false)
  private int maxRetries;

  @Column(name = "run_at", nullable = false)
  private Instant runAt;

  @Column(name = "lease_id")
  private UUID leaseId;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "output_ref")
  private String outputRef;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getQueueName() {
    return queueName;
  }

  public void setQueueName(String queueName) {
    this.queueName = queueName;
  }

  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public String[] getArgs() {
    return args;
  }

  public void setArgs(String[] args) {
    this.args = args;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Instant getRunAt() {
    return runAt;
  }

  public void setRunAt(Instant runAt) {
    this.runAt = runAt;
  }

  public UUID getLeaseId() {
    return leaseId;
  }

  public void setLeaseId(UUID leaseId) {
    this.leaseId = leaseId;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public void setLeaseExpiresAt(Instant leaseExpiresAt) {
    this.leaseExpiresAt = leaseExpiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public String getOutputRef() {
    return outputRef;
  }

  public void setOutputRef(String outputRef) {
    this.outputRef = outputRef;
  }
}

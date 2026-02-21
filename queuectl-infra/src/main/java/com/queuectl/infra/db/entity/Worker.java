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
@Table(name = "workers")
public class Worker {

  @Id
  @Column(name = "worker_id", nullable = false)
  private UUID workerId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "queues", nullable = false, columnDefinition = "text[]")
  private String[] queues = new String[0];

  @Column(name = "last_seen", nullable = false)
  private Instant lastSeen;

  public UUID getWorkerId() {
    return workerId;
  }

  public void setWorkerId(UUID workerId) {
    this.workerId = workerId;
  }

  public String[] getQueues() {
    return queues;
  }

  public void setQueues(String[] queues) {
    this.queues = queues;
  }

  public Instant getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(Instant lastSeen) {
    this.lastSeen = lastSeen;
  }
}

package com.queuectl.domain;

public enum JobState {
  PENDING,
  LEASED,
  PROCESSING,
  COMPLETED,
  FAILED,
  DEAD,
  CANCELED
}

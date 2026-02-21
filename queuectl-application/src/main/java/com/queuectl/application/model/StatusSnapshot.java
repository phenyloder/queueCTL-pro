package com.queuectl.application.model;

import com.queuectl.domain.JobState;
import java.util.Map;

public record StatusSnapshot(Map<JobState, Long> countsByState, int activeWorkers, long dlqSize) {}

package com.queuectl.application.model;

import java.time.Duration;
import java.util.List;

public record WorkerOptions(
    int count,
    List<String> queues,
    Duration leaseTtl,
    Duration heartbeat,
    Duration poll,
    int metricsPort) {}

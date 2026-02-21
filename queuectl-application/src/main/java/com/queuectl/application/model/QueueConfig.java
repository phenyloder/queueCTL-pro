package com.queuectl.application.model;

import java.util.Set;

public record QueueConfig(
    int defaultMaxRetries,
    int backoffBase,
    int maxDelaySeconds,
    int jobTimeoutSeconds,
    long maxOutputBytes,
    Set<String> allowedCommands) {}

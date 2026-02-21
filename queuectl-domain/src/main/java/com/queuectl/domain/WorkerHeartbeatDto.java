package com.queuectl.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkerHeartbeatDto(UUID workerId, List<String> queues, Instant lastSeen) {}

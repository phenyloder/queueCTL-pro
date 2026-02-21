package com.queuectl.domain;

import java.time.Instant;
import java.util.UUID;

public record DlqEntryDto(UUID id, UUID jobId, String reason, Instant movedAt) {}

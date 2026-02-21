package com.queuectl.application.service;

import com.queuectl.domain.DlqEntryDto;
import java.util.List;
import java.util.UUID;

public interface DlqService {

  List<DlqEntryDto> list(int limit);

  boolean retry(UUID jobId, boolean resetAttempts);
}

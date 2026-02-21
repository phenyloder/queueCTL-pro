package com.queuectl.application.repository;

import com.queuectl.domain.DlqEntryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DlqRepository {

  List<DlqEntryDto> listDlq(int limit);

  boolean retryDlqJob(UUID jobId, boolean resetAttempts, Instant now);

  long countDlq();
}

package com.queuectl.infra.db.repository.mapper;

import com.queuectl.domain.DlqEntryDto;
import com.queuectl.infra.db.entity.DlqEntry;

public final class DlqEntryMapper {

  public DlqEntryDto toDomain(DlqEntry entry) {
    return new DlqEntryDto(entry.getId(), entry.getJobId(), entry.getReason(), entry.getMovedAt());
  }
}

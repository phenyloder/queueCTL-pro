package com.queuectl.infra.db.repository;

import com.queuectl.application.repository.DlqRepository;
import com.queuectl.domain.DlqEntryDto;
import com.queuectl.domain.JobState;
import com.queuectl.infra.db.entity.DlqEntry;
import com.queuectl.infra.db.repository.mapper.DlqEntryMapper;
import com.queuectl.infra.db.repository.support.JobStateCodec;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DlqRepositoryImpl extends RepositorySupport implements DlqRepository {

  private final DlqEntryMapper dlqEntryMapper = new DlqEntryMapper();

  public DlqRepositoryImpl(EntityManagerFactory entityManagerFactory) {
    super(entityManagerFactory);
  }

  @Override
  public List<DlqEntryDto> listDlq(int limit) {
    return inEntityManager(
            entityManager ->
                entityManager
                    .createQuery("select d from DlqEntry d order by d.movedAt desc", DlqEntry.class)
                    .setMaxResults(limit)
                    .getResultList())
        .stream()
        .map(dlqEntryMapper::toDomain)
        .toList();
  }

  @Override
  public boolean retryDlqJob(UUID jobId, boolean resetAttempts, Instant now) {
    return inTransaction(
        entityManager -> {
          String updateStatement =
              resetAttempts
                  ? """
                    update Job j
                    set j.state = :pending,
                        j.attempts = 0,
                        j.runAt = :now,
                        j.leaseId = null,
                        j.leaseExpiresAt = null,
                        j.lastError = null,
                        j.updatedAt = :now
                    where j.id = :jobId
                      and j.state = :dead
                    """
                  : """
                    update Job j
                    set j.state = :pending,
                        j.runAt = :now,
                        j.leaseId = null,
                        j.leaseExpiresAt = null,
                        j.lastError = null,
                        j.updatedAt = :now
                    where j.id = :jobId
                      and j.state = :dead
                    """;

          int updated =
              entityManager
                  .createQuery(updateStatement)
                  .setParameter("pending", JobStateCodec.toDbState(JobState.PENDING))
                  .setParameter("dead", JobStateCodec.toDbState(JobState.DEAD))
                  .setParameter("jobId", jobId)
                  .setParameter("now", now)
                  .executeUpdate();

          if (updated == 0) {
            return false;
          }

          entityManager
              .createQuery("delete from DlqEntry d where d.jobId = :jobId")
              .setParameter("jobId", jobId)
              .executeUpdate();
          return true;
        });
  }

  @Override
  public long countDlq() {
    return inEntityManager(
        entityManager ->
            entityManager
                .createQuery("select count(d) from DlqEntry d", Long.class)
                .getSingleResult());
  }
}

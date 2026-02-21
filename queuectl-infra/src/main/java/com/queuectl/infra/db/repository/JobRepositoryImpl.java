package com.queuectl.infra.db.repository;

import com.queuectl.application.repository.JobRepository;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import com.queuectl.infra.db.entity.DlqEntry;
import com.queuectl.infra.db.entity.Job;
import com.queuectl.infra.db.repository.mapper.JobMapper;
import com.queuectl.infra.db.repository.support.JobStateCodec;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;

public final class JobRepositoryImpl extends RepositorySupport implements JobRepository {

  private final JobMapper jobMapper = new JobMapper();

  public JobRepositoryImpl(EntityManagerFactory entityManagerFactory) {
    super(entityManagerFactory);
  }

  @Override
  public JobDto enqueue(
      String queue, String command, List<String> args, int maxRetries, Instant runAt, Instant now) {
    Job entity = jobMapper.toNewEntity(queue, command, args, maxRetries, runAt, now);
    inTransactionVoid(entityManager -> entityManager.persist(entity));
    return jobMapper.toDomain(entity);
  }

  @Override
  public List<JobDto> leaseJobs(
      List<String> queues, int limit, UUID leaseId, Duration leaseTtl, Instant now) {
    if (queues.isEmpty() || limit <= 0) {
      return List.of();
    }

    return inTransaction(
        entityManager -> {
          Session session = entityManager.unwrap(Session.class);
          return session.doReturningWork(
              connection -> {
                String sql =
                    """
                                WITH candidate AS (
                                    SELECT id
                                    FROM jobs
                                    WHERE queue = ANY(?::text[])
                                      AND run_at <= ?::timestamptz
                                      AND (
                                          state IN ('pending', 'failed')
                                          OR (state IN ('leased', 'processing')
                                              AND lease_expires_at IS NOT NULL
                                              AND lease_expires_at <= ?::timestamptz)
                                      )
                                    ORDER BY run_at ASC
                                    LIMIT ?
                                    FOR UPDATE SKIP LOCKED
                                )
                                UPDATE jobs j
                                SET state = 'leased',
                                    lease_id = ?::uuid,
                                    lease_expires_at = ?::timestamptz,
                                    updated_at = ?::timestamptz
                                FROM candidate c
                                WHERE j.id = c.id
                                RETURNING j.id, j.queue, j.command, j.args, j.state, j.attempts, j.max_retries, j.run_at,
                                          j.lease_id, j.lease_expires_at, j.created_at, j.updated_at, j.last_error, j.output_ref
                            """;

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                  Array queueArray =
                      connection.createArrayOf("text", queues.toArray(String[]::new));
                  try {
                    statement.setArray(1, queueArray);
                    statement.setObject(2, JobMapper.asOffset(now));
                    statement.setObject(3, JobMapper.asOffset(now));
                    statement.setInt(4, limit);
                    statement.setObject(5, leaseId);
                    statement.setObject(6, JobMapper.asOffset(now.plus(leaseTtl)));
                    statement.setObject(7, JobMapper.asOffset(now));

                    try (ResultSet resultSet = statement.executeQuery()) {
                      List<JobDto> jobs = new ArrayList<>();
                      while (resultSet.next()) {
                        jobs.add(jobMapper.fromResultSet(resultSet));
                      }
                      return jobs;
                    }
                  } finally {
                    queueArray.free();
                  }
                } catch (SQLException exception) {
                  throw new IllegalStateException("Failed to lease jobs", exception);
                }
              });
        });
  }

  @Override
  public boolean markProcessing(UUID jobId, UUID leaseId, Instant now) {
    int updated =
        inTransaction(
            entityManager ->
                entityManager
                    .createQuery(
                        """
                        update Job j
                        set j.state = :nextState,
                            j.updatedAt = :now
                        where j.id = :jobId
                          and j.leaseId = :leaseId
                          and j.state = :currentState
                        """)
                    .setParameter("nextState", JobStateCodec.toDbState(JobState.PROCESSING))
                    .setParameter("now", now)
                    .setParameter("jobId", jobId)
                    .setParameter("leaseId", leaseId)
                    .setParameter("currentState", JobStateCodec.toDbState(JobState.LEASED))
                    .executeUpdate());
    return updated > 0;
  }

  @Override
  public boolean ackJob(UUID jobId, UUID leaseId, Instant now, String outputRef) {
    int updated =
        inTransaction(
            entityManager ->
                entityManager
                    .createQuery(
                        """
                        update Job j
                        set j.state = :nextState,
                            j.leaseId = null,
                            j.leaseExpiresAt = null,
                            j.lastError = null,
                            j.outputRef = :outputRef,
                            j.updatedAt = :now
                        where j.id = :jobId
                          and j.leaseId = :leaseId
                          and j.state in :currentStates
                        """)
                    .setParameter("nextState", JobStateCodec.toDbState(JobState.COMPLETED))
                    .setParameter(
                        "currentStates",
                        List.of(
                            JobStateCodec.toDbState(JobState.LEASED),
                            JobStateCodec.toDbState(JobState.PROCESSING)))
                    .setParameter("outputRef", outputRef)
                    .setParameter("now", now)
                    .setParameter("jobId", jobId)
                    .setParameter("leaseId", leaseId)
                    .executeUpdate());

    return updated > 0;
  }

  @Override
  public boolean nackJob(
      UUID jobId,
      UUID leaseId,
      Instant now,
      Instant nextRunAt,
      String lastError,
      String outputRef) {
    int updated =
        inTransaction(
            entityManager ->
                entityManager
                    .createQuery(
                        """
                        update Job j
                        set j.state = :nextState,
                            j.attempts = j.attempts + 1,
                            j.runAt = :nextRunAt,
                            j.leaseId = null,
                            j.leaseExpiresAt = null,
                            j.lastError = :lastError,
                            j.outputRef = :outputRef,
                            j.updatedAt = :now
                        where j.id = :jobId
                          and j.leaseId = :leaseId
                          and j.state in :currentStates
                        """)
                    .setParameter("nextState", JobStateCodec.toDbState(JobState.FAILED))
                    .setParameter(
                        "currentStates",
                        List.of(
                            JobStateCodec.toDbState(JobState.LEASED),
                            JobStateCodec.toDbState(JobState.PROCESSING)))
                    .setParameter("nextRunAt", nextRunAt)
                    .setParameter("lastError", lastError)
                    .setParameter("outputRef", outputRef)
                    .setParameter("now", now)
                    .setParameter("jobId", jobId)
                    .setParameter("leaseId", leaseId)
                    .executeUpdate());
    return updated > 0;
  }

  @Override
  public boolean moveToDead(
      UUID jobId, UUID leaseId, Instant now, String reason, String outputRef) {
    return inTransaction(
        entityManager -> {
          int updated =
              entityManager
                  .createQuery(
                      """
                      update Job j
                      set j.state = :nextState,
                          j.attempts = j.attempts + 1,
                          j.leaseId = null,
                          j.leaseExpiresAt = null,
                          j.lastError = :reason,
                          j.outputRef = :outputRef,
                          j.updatedAt = :now
                      where j.id = :jobId
                        and j.leaseId = :leaseId
                        and j.state in :currentStates
                      """)
                  .setParameter("nextState", JobStateCodec.toDbState(JobState.DEAD))
                  .setParameter(
                      "currentStates",
                      List.of(
                          JobStateCodec.toDbState(JobState.LEASED),
                          JobStateCodec.toDbState(JobState.PROCESSING)))
                  .setParameter("reason", reason)
                  .setParameter("outputRef", outputRef)
                  .setParameter("now", now)
                  .setParameter("jobId", jobId)
                  .setParameter("leaseId", leaseId)
                  .executeUpdate();

          if (updated == 0) {
            return false;
          }

          DlqEntry entry = new DlqEntry();
          entry.setId(UUID.randomUUID());
          entry.setJobId(jobId);
          entry.setReason(reason);
          entry.setMovedAt(now);
          entityManager.persist(entry);
          return true;
        });
  }

  @Override
  public Optional<JobDto> getJob(UUID jobId) {
    return inEntityManager(
        entityManager ->
            Optional.ofNullable(entityManager.find(Job.class, jobId))
                .map(jobMapper::toDomain));
  }

  @Override
  public boolean cancelJob(UUID jobId, Instant now) {
    int updated =
        inTransaction(
            entityManager ->
                entityManager
                    .createQuery(
                        """
                        update Job j
                        set j.state = :nextState,
                            j.leaseId = null,
                            j.leaseExpiresAt = null,
                            j.updatedAt = :now
                        where j.id = :jobId
                          and j.state not in :terminalStates
                        """)
                    .setParameter("nextState", JobStateCodec.toDbState(JobState.CANCELED))
                    .setParameter(
                        "terminalStates",
                        List.of(
                            JobStateCodec.toDbState(JobState.COMPLETED),
                            JobStateCodec.toDbState(JobState.DEAD),
                            JobStateCodec.toDbState(JobState.CANCELED)))
                    .setParameter("now", now)
                    .setParameter("jobId", jobId)
                    .executeUpdate());
    return updated > 0;
  }

  @Override
  public List<JobDto> listJobs(Optional<JobState> state, Optional<String> queue, int limit) {
    return inEntityManager(
        entityManager -> {
          StringBuilder query = new StringBuilder("select j from Job j");
          List<String> filters = new ArrayList<>();
          if (state.isPresent()) {
            filters.add("j.state = :state");
          }
          if (queue.isPresent()) {
            filters.add("j.queueName = :queue");
          }
          if (!filters.isEmpty()) {
            query.append(" where ").append(String.join(" and ", filters));
          }
          query.append(" order by j.createdAt desc");

          TypedQuery<Job> typedQuery =
              entityManager.createQuery(query.toString(), Job.class).setMaxResults(limit);
          state.ifPresent(
              value -> typedQuery.setParameter("state", JobStateCodec.toDbState(value)));
          queue.ifPresent(value -> typedQuery.setParameter("queue", value));

          return typedQuery.getResultList().stream().map(jobMapper::toDomain).toList();
        });
  }

  @Override
  public Map<JobState, Long> countByState() {
    Map<JobState, Long> counts = new EnumMap<>(JobState.class);

    inEntityManager(
            entityManager ->
                entityManager
                    .createQuery(
                        "select j.state, count(j) from Job j group by j.state",
                        Object[].class)
                    .getResultList())
        .forEach(
            row ->
                counts.put(
                    JobStateCodec.fromDbState((String) row[0]), ((Number) row[1]).longValue()));

    for (JobState state : JobState.values()) {
      counts.putIfAbsent(state, 0L);
    }

    return counts;
  }
}

package com.queuectl.infra.db.repository;

import com.queuectl.application.repository.WorkerRepository;
import com.queuectl.infra.db.entity.Worker;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkerRepositoryImpl extends RepositorySupport implements WorkerRepository {

  public WorkerRepositoryImpl(EntityManagerFactory entityManagerFactory) {
    super(entityManagerFactory);
  }

  @Override
  public int countActiveWorkers(Duration activeWithin, Instant now) {
    Instant threshold = now.minus(activeWithin);
    long count =
        inEntityManager(
            entityManager ->
                entityManager
                    .createQuery(
                        "select count(w) from Worker w where w.lastSeen >= :threshold",
                        Long.class)
                    .setParameter("threshold", threshold)
                    .getSingleResult());
    return (int) count;
  }

  @Override
  public void upsertWorkerHeartbeat(UUID workerId, List<String> queues, Instant now) {
    inTransactionVoid(
        entityManager -> {
          Worker worker = new Worker();
          worker.setWorkerId(workerId);
          worker.setQueues(queues.toArray(String[]::new));
          worker.setLastSeen(now);
          entityManager.merge(worker);
        });
  }

  @Override
  public void removeWorkerHeartbeat(UUID workerId) {
    inTransactionVoid(
        entityManager -> {
          Worker worker = entityManager.find(Worker.class, workerId);
          if (worker != null) {
            entityManager.remove(worker);
          }
        });
  }
}

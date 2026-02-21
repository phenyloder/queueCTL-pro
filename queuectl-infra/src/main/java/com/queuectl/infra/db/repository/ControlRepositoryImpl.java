package com.queuectl.infra.db.repository;

import com.queuectl.application.repository.ControlRepository;
import com.queuectl.infra.db.entity.Control;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;

public final class ControlRepositoryImpl extends RepositorySupport implements ControlRepository {

  private static final String SHUTDOWN_REQUESTED = "shutdown_requested";

  public ControlRepositoryImpl(EntityManagerFactory entityManagerFactory) {
    super(entityManagerFactory);
  }

  @Override
  public void setShutdownRequested(boolean shutdownRequested, Instant now) {
    inTransactionVoid(
        entityManager -> {
          Control control = entityManager.find(Control.class, SHUTDOWN_REQUESTED);
          if (control == null) {
            control = new Control();
            control.setKey(SHUTDOWN_REQUESTED);
          }
          control.setValue(Boolean.toString(shutdownRequested));
          control.setUpdatedAt(now);
          entityManager.merge(control);
        });
  }

  @Override
  public boolean isShutdownRequested() {
    return inEntityManager(
        entityManager -> {
          Control control = entityManager.find(Control.class, SHUTDOWN_REQUESTED);
          return control != null && Boolean.parseBoolean(control.getValue());
        });
  }
}

package com.queuectl.infra.db.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.util.function.Consumer;
import java.util.function.Function;

abstract class RepositorySupport {

  private final EntityManagerFactory entityManagerFactory;

  RepositorySupport(EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
  }

  protected <T> T inEntityManager(Function<EntityManager, T> operation) {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    try {
      return operation.apply(entityManager);
    } finally {
      entityManager.close();
    }
  }

  protected void inTransactionVoid(Consumer<EntityManager> operation) {
    inTransaction(
        entityManager -> {
          operation.accept(entityManager);
          return null;
        });
  }

  protected <T> T inTransaction(Function<EntityManager, T> operation) {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    EntityTransaction transaction = entityManager.getTransaction();
    transaction.begin();
    try {
      T result = operation.apply(entityManager);
      transaction.commit();
      return result;
    } catch (RuntimeException exception) {
      if (transaction.isActive()) {
        transaction.rollback();
      }
      throw exception;
    } finally {
      entityManager.close();
    }
  }
}

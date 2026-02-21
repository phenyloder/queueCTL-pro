package com.queuectl.infra.db.repository;

import com.queuectl.application.repository.ConfigRepository;
import com.queuectl.infra.db.entity.Config;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Optional;

public final class ConfigRepositoryImpl extends RepositorySupport implements ConfigRepository {

  public ConfigRepositoryImpl(EntityManagerFactory entityManagerFactory) {
    super(entityManagerFactory);
  }

  @Override
  public void setConfig(String key, String value, Instant now) {
    inTransactionVoid(
        entityManager -> {
          Config config = entityManager.find(Config.class, key);
          if (config == null) {
            config = new Config();
            config.setKey(key);
          }
          config.setValue(value);
          config.setUpdatedAt(now);
          entityManager.merge(config);
        });
  }

  @Override
  public Optional<String> getConfig(String key) {
    return inEntityManager(
        entityManager ->
            Optional.ofNullable(entityManager.find(Config.class, key)).map(Config::getValue));
  }
}

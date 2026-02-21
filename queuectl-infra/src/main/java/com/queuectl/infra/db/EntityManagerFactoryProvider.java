package com.queuectl.infra.db;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

public final class EntityManagerFactoryProvider {

  private static final String PERSISTENCE_UNIT = "queuectl-persistence";

  private EntityManagerFactoryProvider() {}

  public static EntityManagerFactory create(DataSource dataSource) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("jakarta.persistence.nonJtaDataSource", dataSource);
    return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT, properties);
  }
}

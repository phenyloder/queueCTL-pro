package com.queuectl.infra.db;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

public final class FlywayMigrator {

  private FlywayMigrator() {}

  public static void migrate(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()
        .migrate();
  }
}

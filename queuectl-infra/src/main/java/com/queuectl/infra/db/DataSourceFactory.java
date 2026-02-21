package com.queuectl.infra.db;

import com.queuectl.infra.config.DbSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public final class DataSourceFactory {

  private DataSourceFactory() {}

  public static DataSource create(DbSettings settings) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(settings.jdbcUrl());
    config.setUsername(settings.username());
    config.setPassword(settings.password());
    config.setMaximumPoolSize(settings.poolSize());
    config.setPoolName("queuectl-hikari");
    config.setAutoCommit(true);
    return new HikariDataSource(config);
  }
}

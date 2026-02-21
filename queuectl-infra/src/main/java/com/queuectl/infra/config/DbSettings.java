package com.queuectl.infra.config;

public record DbSettings(String jdbcUrl, String username, String password, int poolSize) {

  public static DbSettings fromEnvironment() {
    String url =
        read("queuectl.db.url", "QUEUECTL_DB_URL", "jdbc:postgresql://localhost:55432/queuectl");
    String username = read("queuectl.db.user", "QUEUECTL_DB_USER", "queuectl");
    String password = read("queuectl.db.password", "QUEUECTL_DB_PASSWORD", "queuectl");
    int poolSize =
        Integer.parseInt(read("queuectl.db.pool-size", "QUEUECTL_DB_POOL_SIZE", "10"), 10);
    return new DbSettings(url, username, password, poolSize);
  }

  private static String read(String property, String env, String defaultValue) {
    String value = System.getProperty(property);
    if (value != null && !value.isBlank()) {
      return value;
    }
    value = System.getenv(env);
    if (value != null && !value.isBlank()) {
      return value;
    }
    return defaultValue;
  }
}

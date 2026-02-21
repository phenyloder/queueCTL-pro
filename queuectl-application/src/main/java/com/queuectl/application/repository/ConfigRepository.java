package com.queuectl.application.repository;

import java.time.Instant;
import java.util.Optional;

public interface ConfigRepository {

  void setConfig(String key, String value, Instant now);

  Optional<String> getConfig(String key);
}

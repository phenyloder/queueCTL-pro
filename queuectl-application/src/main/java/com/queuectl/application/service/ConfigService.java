package com.queuectl.application.service;

import java.util.Optional;

public interface ConfigService {

  void set(String key, String value);

  Optional<String> get(String key);
}

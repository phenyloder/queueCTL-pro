package com.queuectl.application.service.impl;

import com.queuectl.application.repository.ConfigRepository;
import com.queuectl.application.service.ConfigService;
import com.queuectl.application.spi.ClockProvider;
import java.util.Optional;

public final class ConfigServiceImpl implements ConfigService {

  private final ConfigRepository configRepository;
  private final ClockProvider clockProvider;

  public ConfigServiceImpl(ConfigRepository configRepository, ClockProvider clockProvider) {
    this.configRepository = configRepository;
    this.clockProvider = clockProvider;
  }

  @Override
  public void set(String key, String value) {
    configRepository.setConfig(key, value, clockProvider.now());
  }

  @Override
  public Optional<String> get(String key) {
    return configRepository.getConfig(key);
  }
}

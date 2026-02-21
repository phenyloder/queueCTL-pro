package com.queuectl.cli.mvc.controller;

import com.queuectl.application.service.ConfigService;
import java.util.Optional;

public final class ConfigController {

  private final ConfigService configService;

  public ConfigController(ConfigService configService) {
    this.configService = configService;
  }

  public void set(String key, String value) {
    configService.set(key, value);
  }

  public Optional<String> get(String key) {
    return configService.get(key);
  }
}

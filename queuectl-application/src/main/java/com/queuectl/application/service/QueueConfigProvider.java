package com.queuectl.application.service;

import com.queuectl.application.model.QueueConfig;

public interface QueueConfigProvider {

  QueueConfig load(int fallbackJobTimeoutSeconds);
}

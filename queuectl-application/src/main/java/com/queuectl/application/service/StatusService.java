package com.queuectl.application.service;

import com.queuectl.application.model.StatusSnapshot;

public interface StatusService {

  StatusSnapshot getSnapshot();
}

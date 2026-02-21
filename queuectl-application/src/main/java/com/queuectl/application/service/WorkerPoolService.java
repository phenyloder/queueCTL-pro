package com.queuectl.application.service;

import com.queuectl.application.model.WorkerOptions;

public interface WorkerPoolService {

  void runWorkers(WorkerOptions workerOptions);
}

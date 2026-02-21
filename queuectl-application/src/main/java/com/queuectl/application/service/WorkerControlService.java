package com.queuectl.application.service;

public interface WorkerControlService {

  void requestStop();

  void clearStopSignal();
}

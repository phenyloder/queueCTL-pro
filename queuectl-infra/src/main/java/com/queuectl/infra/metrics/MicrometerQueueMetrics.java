package com.queuectl.infra.metrics;

import com.queuectl.application.spi.QueueMetrics;
import com.queuectl.domain.JobState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class MicrometerQueueMetrics implements QueueMetrics {

  private final PrometheusMeterRegistry meterRegistry;
  private final Counter enqueued;
  private final Counter leased;
  private final Counter completed;
  private final Counter failed;
  private final Counter dead;

  private final AtomicLong pendingGauge = new AtomicLong();
  private final AtomicLong leasedGauge = new AtomicLong();
  private final AtomicLong processingGauge = new AtomicLong();
  private final AtomicLong dlqGauge = new AtomicLong();
  private final AtomicLong activeWorkersGauge = new AtomicLong();

  public MicrometerQueueMetrics() {
    this.meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    this.enqueued = Counter.builder("jobs_enqueued_total").register(meterRegistry);
    this.leased = Counter.builder("jobs_leased_total").register(meterRegistry);
    this.completed = Counter.builder("jobs_completed_total").register(meterRegistry);
    this.failed = Counter.builder("jobs_failed_total").register(meterRegistry);
    this.dead = Counter.builder("jobs_dead_total").register(meterRegistry);

    Gauge.builder("jobs_pending", pendingGauge::get).register(meterRegistry);
    Gauge.builder("jobs_leased", leasedGauge::get).register(meterRegistry);
    Gauge.builder("jobs_processing", processingGauge::get).register(meterRegistry);
    Gauge.builder("dlq_size", dlqGauge::get).register(meterRegistry);
    Gauge.builder("active_workers", activeWorkersGauge::get).register(meterRegistry);
  }

  @Override
  public void onEnqueued(String queue) {
    enqueued.increment();
  }

  @Override
  public void onLeased(String queue) {
    leased.increment();
  }

  @Override
  public void onCompleted(String queue) {
    completed.increment();
  }

  @Override
  public void onFailed(String queue) {
    failed.increment();
  }

  @Override
  public void onDead(String queue) {
    dead.increment();
  }

  @Override
  public void refreshGauges(Map<JobState, Long> countsByState, long dlqSize, int activeWorkers) {
    pendingGauge.set(countsByState.getOrDefault(JobState.PENDING, 0L));
    leasedGauge.set(countsByState.getOrDefault(JobState.LEASED, 0L));
    processingGauge.set(countsByState.getOrDefault(JobState.PROCESSING, 0L));
    dlqGauge.set(dlqSize);
    activeWorkersGauge.set(activeWorkers);
  }

  public String scrape() {
    return meterRegistry.scrape();
  }
}

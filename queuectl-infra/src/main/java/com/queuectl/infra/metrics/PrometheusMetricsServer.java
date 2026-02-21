package com.queuectl.infra.metrics;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrometheusMetricsServer implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsServer.class);

  private HttpServer server;
  private ExecutorService executor;

  public synchronized void start(int port, MicrometerQueueMetrics metrics) {
    if (server != null) {
      return;
    }
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
      executor = Executors.newVirtualThreadPerTaskExecutor();
      server.createContext(
          "/metrics",
          exchange -> {
            byte[] body = metrics.scrape().getBytes(StandardCharsets.UTF_8);
            exchange
                .getResponseHeaders()
                .add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.setExecutor(executor);
      server.start();
      LOGGER.info("metrics_server_started port={}", port);
    } catch (IOException ioException) {
      throw new IllegalStateException("Failed to start metrics server", ioException);
    }
  }

  @Override
  public synchronized void close() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }
}

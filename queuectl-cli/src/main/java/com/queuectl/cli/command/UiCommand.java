package com.queuectl.cli.command;

import com.queuectl.cli.bootstrap.QueueCTLFacade;
import com.queuectl.cli.command.support.ContextCommandSupport;
import com.queuectl.cli.mvc.controller.DashboardController;
import com.queuectl.cli.mvc.view.ConsoleView;
import com.queuectl.cli.mvc.view.DashboardJsonView;
import com.queuectl.cli.mvc.view.DashboardPageView;
import com.queuectl.domain.JobDto;
import com.queuectl.domain.JobState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "ui",
    description = "Web UI",
    mixinStandardHelpOptions = true,
    subcommands = {UiCommand.Start.class})
public final class UiCommand implements Runnable {

  @Override
  public void run() {
    new ConsoleView(System.out).showUsageHint("Use ui start");
  }

  @Command(name = "start", description = "Start web UI", mixinStandardHelpOptions = true)
  static final class Start extends ContextCommandSupport implements Runnable {

    @Option(names = "--port", defaultValue = "8080", description = "UI HTTP port")
    private int port;

    @Option(names = "--host", defaultValue = "127.0.0.1", description = "Bind host")
    private String host;

    @Option(names = "--limit", defaultValue = "300", description = "Max jobs returned by UI API")
    private int limit;

    @Option(names = "--refresh-ms", defaultValue = "2000", description = "Auto refresh interval")
    private long refreshMs;

    @Override
    public void run() {
      QueueCTLFacade module = createModule(false);
      DashboardController controller = module.dashboardController();
      DashboardPageView pageView = new DashboardPageView();
      DashboardJsonView jsonView = new DashboardJsonView();

      HttpServer server;
      try {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
      } catch (IOException ioException) {
        module.close();
        throw new IllegalStateException("Unable to start UI server", ioException);
      }

      AtomicBoolean closed = new AtomicBoolean(false);
      Runnable closer =
          () -> {
            if (closed.compareAndSet(false, true)) {
              server.stop(0);
              module.close();
            }
          };

      server.createContext(
          "/",
          exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
              sendText(exchange, 405, "Method not allowed");
              return;
            }
            sendHtml(exchange, pageView.render(refreshMs));
          });

      server.createContext(
          "/api/status",
          exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
              sendText(exchange, 405, "Method not allowed");
              return;
            }
            sendJson(exchange, jsonView.renderStatus(controller.status()));
          });

      server.createContext(
          "/api/jobs",
          exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
              sendText(exchange, 405, "Method not allowed");
              return;
            }
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            Optional<JobState> state =
                Optional.ofNullable(params.get("state"))
                    .filter(value -> !value.isBlank())
                    .flatMap(Start::parseState);
            Optional<String> queue =
                Optional.ofNullable(params.get("queue")).filter(value -> !value.isBlank());
            int effectiveLimit =
                Math.max(
                    1, Math.min(2000, parseIntOrDefault(params.get("limit"), Math.max(1, limit))));
            List<JobDto> jobs = controller.jobs(state, queue, effectiveLimit);
            sendJson(exchange, jsonView.renderJobs(jobs));
          });

      server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      Runtime.getRuntime().addShutdownHook(new Thread(closer));
      server.start();

      System.out.printf(
          "UI started at http://%s:%d (refresh=%dms, limit=%d). Press Ctrl+C to stop.%n",
          host, port, refreshMs, limit);

      try {
        while (!Thread.currentThread().isInterrupted()) {
          Thread.sleep(1_000);
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      } finally {
        closer.run();
      }
    }

    private static Optional<JobState> parseState(String value) {
      try {
        return Optional.of(JobState.valueOf(value.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
        return Optional.empty();
      }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
      if (rawQuery == null || rawQuery.isBlank()) {
        return Map.of();
      }
      Map<String, String> map = new HashMap<>();
      String[] pairs = rawQuery.split("&");
      for (String pair : pairs) {
        if (pair.isBlank()) {
          continue;
        }
        int idx = pair.indexOf('=');
        String key = idx >= 0 ? pair.substring(0, idx) : pair;
        String value = idx >= 0 ? pair.substring(idx + 1) : "";
        map.put(urlDecode(key), urlDecode(value));
      }
      return map;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
      if (value == null || value.isBlank()) {
        return defaultValue;
      }
      try {
        return Integer.parseInt(value, 10);
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }

    private static String urlDecode(String value) {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendHtml(HttpExchange exchange, String content) throws IOException {
      send(exchange, 200, "text/html; charset=utf-8", content);
    }

    private static void sendJson(HttpExchange exchange, String content) throws IOException {
      send(exchange, 200, "application/json; charset=utf-8", content);
    }

    private static void sendText(HttpExchange exchange, int status, String content)
        throws IOException {
      send(exchange, status, "text/plain; charset=utf-8", content);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String content)
        throws IOException {
      byte[] body = content.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.sendResponseHeaders(status, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    }
  }
}

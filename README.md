# queuectl

`queuectl` is a production-grade CLI background job queue built with Java 21, Picocli, PostgreSQL, JPA/Hibernate, Flyway, virtual-thread workers, and Testcontainers integration tests.

## Tech Stack

- Java 21
- Gradle (wrapper committed)
- Picocli (CLI)
- PostgreSQL + JPA (Hibernate) + HikariCP
- Flyway migrations
- Virtual Threads (worker concurrency)
- JUnit 5 + Testcontainers + Awaitility
- SLF4J + Logback
- Micrometer + Prometheus endpoint
- Spotless + Checkstyle

## Quick Start

### 1) Start PostgreSQL

```bash
docker compose up -d postgres
```

Default host port mapping in compose is `55432` (to avoid conflicts with local PostgreSQL on `5432`).
You can override with `QUEUECTL_PG_PORT`, for example:

```bash
QUEUECTL_PG_PORT=55432 docker compose up -d postgres
```

Default DB connection used by CLI:

- URL: `jdbc:postgresql://localhost:55432/queuectl`
- User: `queuectl`
- Password: `queuectl`

Override via environment variables:

- `QUEUECTL_DB_URL`
- `QUEUECTL_DB_USER`
- `QUEUECTL_DB_PASSWORD`
- `QUEUECTL_DB_POOL_SIZE`

### 2) Build and test

```bash
./gradlew clean test
./gradlew spotlessCheck
```

### 3) Install CLI distribution

```bash
./gradlew :queuectl-cli:installDist
```

CLI binary path:

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl
```

## CLI Usage

### Enqueue

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl enqueue \
  --queue default \
  --command "echo" \
  --arg "hello" \
  --max-retries 3 \
  --run-at 2026-02-15T12:00:00Z
```

Expected output (example):

```text
Enqueued job c2e4c5f8-9f85-4eeb-b8e4-3f52931bd373 in queue default (state=PENDING)
```

### Start workers

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl worker start \
  --count 4 \
  --queues default,emails \
  --lease-ttl 30s \
  --heartbeat 5s \
  --poll 250ms
```

Prometheus metrics are exposed at `http://127.0.0.1:9090/metrics` by default while workers run.

### Stop workers gracefully

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl worker stop
```

Expected output:

```text
Shutdown requested. Workers will stop gracefully.
```

### Queue status

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl status
```

Expected output (example):

```text
Active workers: 1
pending    0
leased     0
processing 0
completed  3
failed     0
dead       0
canceled   0
dlq: 0
```

### List jobs

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl list --state pending --queue default --limit 50
```

### DLQ operations

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl dlq list --limit 50
./queuectl-cli/build/install/queuectl-cli/bin/queuectl dlq retry <jobId>
```

### Config operations

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl config set max-retries 5
./queuectl-cli/build/install/queuectl-cli/bin/queuectl config set backoff-base 2
./queuectl-cli/build/install/queuectl-cli/bin/queuectl config set max-delay-seconds 300
./queuectl-cli/build/install/queuectl-cli/bin/queuectl config get max-retries
```

### Job inspect / cancel

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl job inspect <jobId>
./queuectl-cli/build/install/queuectl-cli/bin/queuectl job cancel <jobId>
```

### Web UI (running + past jobs)

```bash
./queuectl-cli/build/install/queuectl-cli/bin/queuectl ui start --port 8080
```

Then open `http://127.0.0.1:8080` to view:

- live state counts
- active workers
- running jobs (`leased`, `processing`)
- historical jobs (`completed`, `failed`, `dead`, `canceled`)

## Architecture Overview

See `docs/architecture.md` for module boundaries, MVC layering, state machine, leasing algorithm, retry policy, DLQ handling, and observability.
Runtime flow is `Controller -> Service Interface -> Repository/SPI Contract -> JPA/Infra Implementation`.

## Trade-offs

- Focused on PostgreSQL concurrency semantics (`FOR UPDATE SKIP LOCKED`) for correctness under parallel workers.
- `worker stop` is DB control-plane based (portable across platforms, no PID/signal dependency).
- `attempts` increments on failures; retries are controlled by `max_retries` with dead-lettering after exhaustion.
- Metrics server is local-only (`127.0.0.1`) during worker runtime.

## Testing

Run all tests:

```bash
./gradlew test
```

Run full verification (tests + lint/style):

```bash
./gradlew check
./gradlew spotlessCheck
```

Integration tests (`queuectl-infra`) use Testcontainers PostgreSQL and Awaitility, including:

1. basic successful execution
2. retry/backoff and DLQ move
3. 3-worker no-overlap processing
4. invalid command graceful failure with retries
5. persistence across worker restart

Deterministic manual concurrency + state coverage (pending, processing, completed, failed, dead, canceled):

```bash
./scripts/test-concurrency-states.sh
```

Note: `leased` is intentionally very short-lived in current implementation (`leased -> processing` immediately), so it may not appear in manual snapshots.

Capacity benchmark (estimate max useful concurrent workers on your machine):

```bash
./scripts/benchmark-concurrency-limit.sh
```

Example custom sweep:

```bash
./scripts/benchmark-concurrency-limit.sh --jobs 120 --sleep-seconds 0.5 --counts 2,4,8,12,16
```

## Demo

Automated demo script:

```bash
./scripts/demo.sh
```

The script performs an end-to-end scenario:

- starts PostgreSQL
- builds and installs the CLI
- enqueues jobs
- starts workers
- prints status/list output
- requests graceful worker shutdown

If you want to run demo against a non-default database, export `QUEUECTL_DB_URL`, `QUEUECTL_DB_USER`, and `QUEUECTL_DB_PASSWORD` before running the script.

## Design decisions

Dependency and design justifications are tracked in `docs/design-decisions.md`.

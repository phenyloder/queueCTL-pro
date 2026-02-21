# queuectl Architecture

## Modules

- `queuectl-domain`
  - Pure domain model and policy (`JobDto`, `DlqEntryDto`, `WorkerHeartbeatDto`, `JobState`, `RetryPolicy`, `JitterType`).
- `queuectl-application`
  - Application services and contracts. Contains orchestration for enqueueing, worker loop, status, config, DLQ replay, and job control.
  - Service interfaces:
    - `JobService`
    - `WorkerPoolService`
    - `WorkerControlService`
    - `StatusService`
    - `DlqService`
    - `ConfigService`
  - Contracts are split by intent:
    - Persistence contracts in `repository`.
    - External system contracts in `spi`.
  - Service implementations are in `service.impl`.
- `queuectl-infra`
  - PostgreSQL/JPA implementations (`Hibernate` + `HikariCP`), migrations (`Flyway`), process execution, and metrics (`Micrometer` + Prometheus endpoint).
- `queuectl-cli`
  - Picocli commands and composition root (manual DI).
  - `QueueCTLFacade` centralizes MVC wiring and lifecycle.
  - MVC-style split:
    - Controllers: `JobController`, `WorkerController`, `StatusController`, `DlqController`, `ConfigController`, `DashboardController`.
    - Views: `ConsoleView`, `DashboardPageView`, `DashboardJsonView`.
  - Includes `ui start` command with embedded HTTP dashboard for job/worker visibility.

## Clean Architecture Boundaries

- CLI depends on application service interfaces only.
- Application depends on focused contracts (`JobRepository`, `DlqRepository`, `ConfigRepository`, `WorkerRepository`, `ControlRepository`, `JobRunner`, `ClockProvider`, `QueueMetrics`) and domain models.
- Infra implements these contracts and owns all external concerns (SQL, process spawning, Prometheus server).
- SQL and `ProcessBuilder` are isolated from application/CLI layers.
- Infra persistence uses JPA entities for `jobs`, `dlq`, `config`, `workers`, and `control`.
- Infra persistence is split into dedicated repository implementations:
  - `JobRepositoryImpl`
  - `DlqRepositoryImpl`
  - `ConfigRepositoryImpl`
  - `WorkerRepositoryImpl`
  - `ControlRepositoryImpl`
- Infra persistence mapping is modularized with dedicated mappers/codecs:
  - `JobMapper`
  - `DlqEntryMapper`
  - `JobStateCodec`

## Design Patterns

- `MVC` in CLI:
  - Controllers coordinate application service interfaces.
  - Views render console or HTTP responses.
  - Models are domain/application DTO objects.
  - Benefit: clear flow from input handling to business logic to presentation.

- `Repository` in application/infra:
  - Application defines repository contracts.
  - Infra provides JPA-backed implementations.
  - Benefit: persistence is swappable and isolated from orchestration logic.

- `Facade` in bootstrap (`QueueCTLFacade`):
  - Exposes a focused API for commands (`jobController()`, `workerController()`, etc.).
  - Benefit: commands do not manage low-level wiring and can remain thin.

- `Template Method` in command bootstrap (`ContextCommandSupport`):
  - Standardizes module creation/teardown for commands.
  - Benefit: removes repeated setup code and keeps command behavior consistent.

- `Data Mapper` in infra mapping (`JobMapper`, `DlqEntryMapper`):
  - Centralizes entity/result-set to domain mapping.
  - Benefit: repositories keep single responsibility (query/update logic only).

## SOLID Mapping

- `S` (Single Responsibility):
  - Controllers only orchestrate request/response.
  - Services own business logic.
  - Repositories own persistence.
  - Mappers/codecs own translation only.

- `O` (Open/Closed):
  - New storage or runner implementations can be added by implementing contracts (`repository`/`spi`) without changing service logic.

- `L` (Liskov Substitution):
  - Implementations (`*RepositoryImpl`, `NoopQueueMetrics`, `MicrometerQueueMetrics`) are substitutable via shared interfaces.

- `I` (Interface Segregation):
  - Small focused interfaces (`JobService`, `StatusService`, `WorkerPoolService`, `WorkerControlService`, `QueueMetrics`, repositories) avoid forcing consumers to depend on unused methods.

- `D` (Dependency Inversion):
  - High-level policies (application services) depend on abstractions (`repository`, `spi`), not concrete infra classes.
  - CLI controllers depend on service interfaces, while composition root wires concrete implementations.

## Job State Machine

Primary states:

- `pending`
- `leased`
- `processing`
- `completed`
- `failed`
- `dead`
- `canceled`

Transitions:

- `pending|failed|expired leased/processing -> leased` (atomic lease)
- `leased -> processing` (when execution starts)
- `processing|leased -> completed` (ACK)
- `processing|leased -> failed` (NACK + retry schedule when `attempts + 1 <= max_retries`)
- `processing|leased -> dead + dlq row` (when `attempts + 1 > max_retries`)
- `* -> canceled` (manual cancel for non-terminal jobs)

Crash recovery:

- Expired leases (`lease_expires_at <= now`) are considered eligible for re-lease.

## Leasing Algorithm (Concurrency Safe)

The store uses a single SQL statement with CTE and row locking:

1. Select candidate IDs from ready jobs (`run_at <= now`) using queue filters.
2. Lock with `FOR UPDATE SKIP LOCKED`.
3. Update locked rows to `leased` with `lease_id` and `lease_expires_at`.
4. Return leased rows.

This prevents duplicate processing across concurrent workers.

## Worker Model

- Worker pool uses Java 21 virtual threads.
- `--count` controls parallel workers.
- `--poll` controls queue polling interval.
- `--heartbeat` controls worker heartbeat persistence frequency.
- `queuectl worker stop` sets DB control flag `shutdown_requested=true`.
- Workers periodically check the flag and exit gracefully after current loop iteration.

## Retry and Backoff

- Delay formula: `base^attempt` seconds.
- Jitter mode: `FULL` (`random [0, delay]`).
- Delay capped by `max-delay-seconds`.
- Defaults:
  - `backoff-base=2`
  - `max-delay-seconds=300`

## DLQ Model

- Dead jobs are persisted in `jobs` with state `dead`.
- A row is inserted into `dlq` containing reason and timestamp.
- `dlq retry <jobId>` requeues dead jobs to `pending` and removes DLQ entries for that job.

## Config Model

Runtime settings are persisted in `config` table and can be changed by CLI:

- `max-retries`
- `backoff-base`
- `max-delay-seconds`
- `allowed-commands`
- `job-timeout-seconds`
- `max-output-bytes`

Control-plane setting:

- `control.shutdown_requested`

## Execution Safety

- Process execution uses `ProcessBuilder`.
- Command allowlist enforced before execution.
- Stdout/stderr merged and written to bounded output file (`data/outputs/<jobId>.log`).
- Timeout enforced per job (default from config / lease TTL fallback).
- Exit code determines success/failure.

## Observability

Logs:

- enqueue
- lease
- execution start/finish
- nack
- move to DLQ

Metrics (Prometheus):

- Counters:
  - `jobs_enqueued_total`
  - `jobs_leased_total`
  - `jobs_completed_total`
  - `jobs_failed_total`
  - `jobs_dead_total`
- Gauges:
  - `jobs_pending`
  - `jobs_leased`
  - `jobs_processing`
  - `dlq_size`
  - `active_workers`

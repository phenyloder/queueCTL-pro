# queuectl LLD / UML

This document captures low-level UML diagrams from the current implementation across:

- `queuectl-cli`
- `queuectl-application`
- `queuectl-infra`
- `queuectl-domain`

## 1) Sequence Diagram - Enqueue Flow

```mermaid
sequenceDiagram
autonumber
participant CMD as EnqueueCommand
participant SUP as ContextCommandSupport
participant FAC as QueueCTLFacade
participant CTX as QueueCtlContext
participant CTRL as JobController
participant SVC as JobServiceImpl
participant CFG as QueueConfigProviderImpl
participant REPO as JobRepositoryImpl
participant MET as QueueMetrics
participant DB as PostgreSQL

CMD->>SUP: withModule(false, action)
SUP->>FAC: create(false, data/outputs)
FAC->>CTX: create(false, data/outputs)
SUP->>CTRL: jobController()
CMD->>CTRL: enqueue(queue, command, args, maxRetries, runAt)
CTRL->>SVC: enqueue(...)
SVC->>CFG: load(30) (when maxRetries is null)
CFG-->>SVC: QueueConfig.defaultMaxRetries
SVC->>REPO: enqueue(...)
REPO->>DB: INSERT INTO jobs (state=pending,...)
DB-->>REPO: persisted row
REPO-->>SVC: JobDto
SVC->>MET: onEnqueued(queue)
SVC-->>CTRL: JobDto
CTRL-->>CMD: JobDto
```

## 2) Sequence Diagram - Worker Loop and Job Execution

```mermaid
sequenceDiagram
autonumber
participant WC as WorkerCommand.Start
participant CTRL as WorkerController
participant POOL as WorkerPoolServiceImpl
participant WREPO as WorkerRepositoryImpl
participant CREPO as ControlRepositoryImpl
participant JREPO as JobRepositoryImpl
participant RUN as ProcessJobRunner
participant MET as QueueMetrics
participant DREPO as DlqRepositoryImpl
participant DB as PostgreSQL

WC->>CTRL: start(WorkerOptions)
CTRL->>POOL: runWorkers(options)
POOL->>CREPO: setShutdownRequested(false)
loop each worker thread
  POOL->>WREPO: upsertWorkerHeartbeat(workerId, queues, now)
  POOL->>CREPO: isShutdownRequested()
  alt stop requested
    POOL-->>CTRL: return
  else continue
    POOL->>JREPO: leaseJobs(queues, 1, leaseId, leaseTtl, now)
    JREPO->>DB: CTE + FOR UPDATE SKIP LOCKED
    DB-->>JREPO: leased job or empty
    alt no job
      POOL->>POOL: sleep(poll)
    else leased job
      POOL->>MET: onLeased(queue)
      POOL->>JREPO: markProcessing(jobId, leaseId, now)
      POOL->>RUN: run(job, timeout, allowedCommands, maxOutputBytes)
      alt success
        POOL->>JREPO: ackJob(...)
        POOL->>MET: onCompleted(queue)
      else failed and retries remain
        POOL->>JREPO: nackJob(..., nextRunAt)
        POOL->>MET: onFailed(queue)
      else failed and retries exhausted
        POOL->>JREPO: moveToDead(...)
        JREPO->>DB: INSERT INTO dlq
        POOL->>MET: onDead(queue)
      end
      POOL->>JREPO: countByState()
      POOL->>DREPO: countDlq()
      POOL->>WREPO: countActiveWorkers(...)
      POOL->>MET: refreshGauges(...)
    end
  end
end
```

## 3) State Diagram - Job Lifecycle

```mermaid
stateDiagram-v2
[*] --> PENDING

PENDING --> LEASED: leaseJobs
FAILED --> LEASED: leaseJobs
LEASED --> LEASED: lease expires and re-lease
PROCESSING --> LEASED: lease expires and re-lease

LEASED --> PROCESSING: markProcessing

PROCESSING --> COMPLETED: ackJob
LEASED --> COMPLETED: ackJob

PROCESSING --> FAILED: nackJob (attempts <= maxRetries)
LEASED --> FAILED: nackJob (attempts <= maxRetries)

PROCESSING --> DEAD: moveToDead (attempts > maxRetries)
LEASED --> DEAD: moveToDead (attempts > maxRetries)

PENDING --> CANCELED: cancelJob
FAILED --> CANCELED: cancelJob
LEASED --> CANCELED: cancelJob
PROCESSING --> CANCELED: cancelJob

DEAD --> PENDING: retryDlqJob

COMPLETED --> [*]
DEAD --> [*]
CANCELED --> [*]
```

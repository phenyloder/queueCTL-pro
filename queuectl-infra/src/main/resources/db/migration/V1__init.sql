CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY,
    queue TEXT NOT NULL DEFAULT 'default',
    command TEXT NOT NULL,
    args TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    state TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    run_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_id UUID NULL,
    lease_expires_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT NULL,
    output_ref TEXT NULL,
    CONSTRAINT jobs_state_chk CHECK (state IN ('pending', 'leased', 'processing', 'completed', 'failed', 'dead', 'canceled'))
);

CREATE TABLE IF NOT EXISTS dlq (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    moved_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workers (
    worker_id UUID PRIMARY KEY,
    queues TEXT[] NOT NULL,
    last_seen TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS control (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_jobs_ready ON jobs (queue, state, run_at);
CREATE INDEX IF NOT EXISTS idx_jobs_lease_expires_at ON jobs (lease_expires_at);
CREATE INDEX IF NOT EXISTS idx_jobs_state ON jobs (state);
CREATE INDEX IF NOT EXISTS idx_dlq_moved_at ON dlq (moved_at DESC);

INSERT INTO config (key, value, updated_at)
VALUES
    ('max-retries', '3', NOW()),
    ('backoff-base', '2', NOW()),
    ('max-delay-seconds', '300', NOW()),
    ('allowed-commands', 'echo,sleep', NOW()),
    ('max-output-bytes', '1000000', NOW())
ON CONFLICT (key) DO NOTHING;

INSERT INTO control (key, value, updated_at)
VALUES ('shutdown_requested', 'false', NOW())
ON CONFLICT (key) DO NOTHING;

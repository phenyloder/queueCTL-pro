#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

QUEUECTL_BIN="./queuectl-cli/build/install/queuectl-cli/bin/queuectl"

printf "[demo] starting postgres...\n"
if [[ -z "${QUEUECTL_DB_URL:-}" ]]; then
  docker compose up -d postgres
  export QUEUECTL_DB_URL="jdbc:postgresql://localhost:${QUEUECTL_PG_PORT:-55432}/queuectl"
  export QUEUECTL_DB_USER="${QUEUECTL_DB_USER:-queuectl}"
  export QUEUECTL_DB_PASSWORD="${QUEUECTL_DB_PASSWORD:-queuectl}"
else
  printf "[demo] using existing database: %s\n" "${QUEUECTL_DB_URL}"
fi

printf "[demo] building and installing cli...\n"
./gradlew :queuectl-cli:installDist >/dev/null

printf "[demo] setting demo config...\n"
"$QUEUECTL_BIN" config set allowed-commands "echo,sleep,doesnotexist"
"$QUEUECTL_BIN" config set backoff-base 1
"$QUEUECTL_BIN" config set max-delay-seconds 1

printf "[demo] enqueueing jobs...\n"
"$QUEUECTL_BIN" enqueue --queue default --command echo --arg "hello from queuectl" --max-retries 1
"$QUEUECTL_BIN" enqueue --queue default --command doesnotexist --max-retries 1

printf "[demo] starting workers...\n"
"$QUEUECTL_BIN" worker start --count 2 --queues default --lease-ttl 30s --heartbeat 5s --poll 200ms > /tmp/queuectl-worker.log 2>&1 &
WORKER_PID=$!

sleep 6

printf "[demo] status after processing:\n"
"$QUEUECTL_BIN" status

printf "[demo] job list:\n"
"$QUEUECTL_BIN" list --limit 20

printf "[demo] dlq list:\n"
"$QUEUECTL_BIN" dlq list --limit 20

printf "[demo] requesting graceful stop...\n"
"$QUEUECTL_BIN" worker stop
wait "$WORKER_PID" || true

printf "[demo] worker log tail:\n"
tail -n 20 /tmp/queuectl-worker.log || true

printf "[demo] done.\n"

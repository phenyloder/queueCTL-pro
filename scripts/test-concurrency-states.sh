#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export QUEUECTL_DB_URL="${QUEUECTL_DB_URL:-jdbc:postgresql://localhost:55432/queuectl}"
export QUEUECTL_DB_USER="${QUEUECTL_DB_USER:-queuectl}"
export QUEUECTL_DB_PASSWORD="${QUEUECTL_DB_PASSWORD:-queuectl}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dorg.jooq.no-logo=true -Dorg.jooq.no-tips=true"

BIN="./queuectl-cli/build/install/queuectl-cli/bin/queuectl"

enqueue_id() {
  local output
  output="$($BIN enqueue "$@")"
  echo "$output" | grep -Eo '[0-9a-fA-F-]{36}' | head -n1
}

printf "[test] start postgres container\n"
docker compose up -d postgres >/dev/null

printf "[test] build cli\n"
./gradlew :queuectl-cli:installDist >/dev/null

printf "[test] reset db\n"
docker exec queuectl-postgres psql -U queuectl -d queuectl -c \
  "TRUNCATE dlq, jobs, workers RESTART IDENTITY; UPDATE control SET value='false', updated_at=NOW() WHERE key='shutdown_requested';" >/dev/null

printf "[test] configure\n"
$BIN config set allowed-commands "echo,sleep,doesnotexist" >/dev/null
$BIN config set backoff-base 10 >/dev/null
$BIN config set max-delay-seconds 300 >/dev/null
$BIN config set max-retries 3 >/dev/null

printf "[test] enqueue workload for concurrency\n"
for i in $(seq 1 16); do
  enqueue_id --queue default --command sleep --arg 2 >/dev/null
done
for i in $(seq 1 16); do
  enqueue_id --queue default --command echo --arg "ok-$i" >/dev/null
done

echo "[test] enqueue state coverage jobs"
DEAD_ID="$(enqueue_id --queue default --command doesnotexist --max-retries 0)"
FAILED_ID="$(enqueue_id --queue default --command doesnotexist --max-retries 3)"

if date -u -v+20M +%Y-%m-%dT%H:%M:%SZ >/dev/null 2>&1; then
  FUTURE_AT="$(date -u -v+20M +%Y-%m-%dT%H:%M:%SZ)"
else
  FUTURE_AT="$(python - <<'PY'
from datetime import datetime, timezone, timedelta
print((datetime.now(timezone.utc) + timedelta(minutes=20)).strftime('%Y-%m-%dT%H:%M:%SZ'))
PY
)"
fi

PENDING_ID="$(enqueue_id --queue default --command echo --arg pending-future --run-at "$FUTURE_AT")"
CANCEL_ID="$(enqueue_id --queue default --command echo --arg cancel-me --run-at "$FUTURE_AT")"
$BIN job cancel "$CANCEL_ID" >/dev/null

printf "[test] ids: dead=%s failedCandidate=%s pending=%s canceled=%s\n" "$DEAD_ID" "$FAILED_ID" "$PENDING_ID" "$CANCEL_ID"

printf "[test] start 4 workers\n"
$BIN worker start --count 4 --queues default --lease-ttl 40s --heartbeat 5s --poll 200ms >/tmp/queuectl-state-test-worker.log 2>&1 &
WORKER_PID=$!

sleep 2

printf "\n=== DURING RUN (parallel processing check) ===\n"
$BIN status
$BIN list --state processing --limit 20 || true

sleep 8

printf "\n=== AFTER RUN (state coverage snapshot) ===\n"
$BIN status

printf "\n[completed]\n"
$BIN list --state completed --limit 10 || true

printf "\n[failed] (transient, usually present due backoff)\n"
$BIN list --state failed --limit 10 || true

printf "\n[dead]\n"
$BIN list --state dead --limit 10 || true

printf "\n[canceled]\n"
$BIN list --state canceled --limit 10 || true

printf "\n[pending]\n"
$BIN list --state pending --limit 10 || true

printf "\n[test] stop workers\n"
$BIN worker stop >/dev/null || true
wait "$WORKER_PID" || true

printf "\n[test] done. Worker log: /tmp/queuectl-state-test-worker.log\n"
printf "[note] leased is intentionally very short-lived in current implementation (leased->processing immediately).\n"
printf "[note] use integration test for strict no-overlap proof:\n"
printf "       ./gradlew :queuectl-infra:test --tests 'com.queuectl.infra.QueueWorkerIntegrationTest.multipleWorkersProcessWithoutOverlap'\n"

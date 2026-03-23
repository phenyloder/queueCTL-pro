#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

QUEUECTL_BIN="./queuectl-cli/build/install/queuectl-cli/bin/queuectl"
UI_HOST="${UI_HOST:-127.0.0.1}"
UI_PORT="${UI_PORT:-8080}"
QUEUES="${QUEUES:-default}"
WORKER_COUNT=4
LEASE_TTL="${LEASE_TTL:-45s}"
HEARTBEAT="${HEARTBEAT:-5s}"
POLL_INTERVAL="${POLL_INTERVAL:-200ms}"
METRICS_PORT="${METRICS_PORT:-9090}"

ensure_cli_bin() {
  if [[ -x "$QUEUECTL_BIN" ]]; then
    return
  fi
  echo "[setup] queuectl binary not found. Building CLI distribution..."
  ./gradlew :queuectl-cli:installDist >/dev/null
}

assert_ui_active() {
  local url="http://${UI_HOST}:${UI_PORT}/api/status"
  if ! curl -fsS "$url" >/dev/null 2>&1; then
    echo "[error] UI is not active at http://${UI_HOST}:${UI_PORT}" >&2
    echo "[hint] run scripts/enqueue-python-heavy-jobs.sh first (it starts UI)." >&2
    exit 1
  fi
}

ensure_cli_bin
assert_ui_active

echo "[worker] starting ${WORKER_COUNT} workers on queues=${QUEUES}"
echo "[worker] use 'queuectl worker stop' from another terminal to stop gracefully"

exec "$QUEUECTL_BIN" worker start \
  --count "$WORKER_COUNT" \
  --queues "$QUEUES" \
  --lease-ttl "$LEASE_TTL" \
  --heartbeat "$HEARTBEAT" \
  --poll "$POLL_INTERVAL" \
  --metrics-port "$METRICS_PORT"

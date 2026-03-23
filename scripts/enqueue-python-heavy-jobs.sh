#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

QUEUECTL_BIN="./queuectl-cli/build/install/queuectl-cli/bin/queuectl"
UI_HOST="${UI_HOST:-127.0.0.1}"
UI_PORT="${UI_PORT:-8080}"
QUEUE_NAME="${QUEUE_NAME:-default}"
JOB_COUNT="${JOB_COUNT:-20}"
MAX_RETRIES="${MAX_RETRIES:-1}"
UI_LOG_PATH="${UI_LOG_PATH:-/tmp/queuectl-ui.log}"
UI_PID_PATH="${UI_PID_PATH:-/tmp/queuectl-ui.pid}"
MATRIX_SIZE="${MATRIX_SIZE:-120}"

PYTHON_JOB_CODE=$'import os\nimport time\n\n\ndef build_matrix(n: int, offset: int) -> list[list[float]]:\n    return [[((r * 17 + c * 31 + offset) % 97) / 97.0 for c in range(n)] for r in range(n)]\n\n\ndef matmul(a: list[list[float]], b: list[list[float]]) -> list[list[float]]:\n    n = len(a)\n    bt = list(zip(*b))\n    out = [[0.0] * n for _ in range(n)]\n    for i, row in enumerate(a):\n        for j, col in enumerate(bt):\n            total = 0.0\n            for x, y in zip(row, col):\n                total += x * y\n            out[i][j] = total\n    return out\n\n\nif __name__ == "__main__":\n    n = '"$MATRIX_SIZE"$'\n    start = time.time()\n    left = build_matrix(n, 3)\n    right = build_matrix(n, 11)\n    result = matmul(left, right)\n    checksum = round(sum(result[i][i] for i in range(n)), 6)\n    elapsed = round(time.time() - start, 3)\n    print(f"pid={os.getpid()} n={n} checksum={checksum} elapsed={elapsed}s")\n'

ensure_cli_bin() {
  if [[ -x "$QUEUECTL_BIN" ]]; then
    return
  fi
  echo "[setup] queuectl binary not found. Building CLI distribution..."
  ./gradlew :queuectl-cli:installDist >/dev/null
}

wait_for_queuectl_db() {
  for _ in $(seq 1 45); do
    if "$QUEUECTL_BIN" status >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  echo "[error] queuectl could not connect to PostgreSQL in time." >&2
  exit 1
}

start_ui_if_needed() {
  local url="http://${UI_HOST}:${UI_PORT}/api/status"
  if curl -fsS "$url" >/dev/null 2>&1; then
    echo "[ui] already active at http://${UI_HOST}:${UI_PORT}"
    return
  fi

  echo "[ui] starting UI at http://${UI_HOST}:${UI_PORT}"
  nohup "$QUEUECTL_BIN" ui start --host "$UI_HOST" --port "$UI_PORT" --limit 500 \
    >"$UI_LOG_PATH" 2>&1 &
  echo $! >"$UI_PID_PATH"

  for _ in $(seq 1 45); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "[ui] active"
      return
    fi
    sleep 1
  done

  echo "[error] UI did not become ready. Check log: $UI_LOG_PATH" >&2
  exit 1
}

ensure_python_allowed() {
  local raw current normalized updated
  raw="$("$QUEUECTL_BIN" config get allowed-commands 2>/dev/null || true)"
  current="${raw#*=}"
  normalized="$(printf '%s' "$current" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"

  if [[ ",$normalized," == *",python3,"* ]]; then
    return
  fi

  if [[ -z "$normalized" || "$normalized" == "<unset>" ]]; then
    updated="python3"
  else
    updated="${normalized},python3"
  fi

  "$QUEUECTL_BIN" config set allowed-commands "$updated" >/dev/null
  echo "[config] allowed-commands updated to include python3"
}

enqueue_jobs() {
  echo "[enqueue] queue=${QUEUE_NAME}, jobs=${JOB_COUNT}, command=python3 -c <matrix-multiply>, matrix_size=${MATRIX_SIZE}"
  for i in $(seq 1 "$JOB_COUNT"); do
    output="$("$QUEUECTL_BIN" enqueue \
      --queue "$QUEUE_NAME" \
      --command python3 \
      --arg -c \
      --arg "$PYTHON_JOB_CODE" \
      --max-retries "$MAX_RETRIES")"
    job_id="$(printf '%s\n' "$output" | grep -Eo '[0-9a-fA-F-]{36}' | head -n1 || true)"
    printf "[enqueue] %02d/%02d %s\n" "$i" "$JOB_COUNT" "${job_id:-<unknown-id>}"
  done
}

echo "[setup] ensuring PostgreSQL is up"
docker compose up -d postgres >/dev/null

ensure_cli_bin
wait_for_queuectl_db
start_ui_if_needed
ensure_python_allowed
enqueue_jobs

echo "[done] ${JOB_COUNT} Python matrix-multiplication jobs enqueued (${QUEUE_NAME})."
echo "[next] run scripts/start-4-workers.sh to process them."

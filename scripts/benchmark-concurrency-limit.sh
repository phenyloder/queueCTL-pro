#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export QUEUECTL_DB_NAME="${QUEUECTL_DB_NAME:-queuectl}"
export QUEUECTL_DB_URL="${QUEUECTL_DB_URL:-jdbc:postgresql://localhost:55432/${QUEUECTL_DB_NAME}}"
export QUEUECTL_DB_USER="${QUEUECTL_DB_USER:-queuectl}"
export QUEUECTL_DB_PASSWORD="${QUEUECTL_DB_PASSWORD:-queuectl}"

BIN="./queuectl-cli/build/install/queuectl-cli/bin/queuectl"

JOBS=80
SLEEP_SECONDS="1"
COUNTS_CSV="1,2,4,8,16"
QUEUE_NAME="default"
LEASE_TTL="45s"
HEARTBEAT="5s"
POLL_INTERVAL="150ms"
WAIT_TIMEOUT_SECONDS=600
BUILD_CLI=true
RESULTS_FILE=""

usage() {
  cat <<'USAGE'
Usage: ./scripts/benchmark-concurrency-limit.sh [options]

Options:
  --jobs <n>                Number of jobs per run (default: 80)
  --sleep-seconds <n>       Sleep duration each job executes (default: 1)
  --counts <csv>            Worker counts to test (default: 1,2,4,8,16)
  --queue <name>            Queue name (default: default)
  --lease-ttl <duration>    Lease TTL passed to worker start (default: 45s)
  --heartbeat <duration>    Heartbeat duration (default: 5s)
  --poll <duration>         Worker poll interval (default: 150ms)
  --timeout-seconds <n>     Max time for each run (default: 600)
  --no-build                Skip :queuectl-cli:installDist
  --results-file <path>     Output CSV path (default: data/concurrency-benchmark-<timestamp>.csv)
  -h, --help                Show help

Examples:
  ./scripts/benchmark-concurrency-limit.sh
  ./scripts/benchmark-concurrency-limit.sh --jobs 120 --sleep-seconds 0.5 --counts 2,4,8,12,16
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jobs)
      JOBS="$2"
      shift 2
      ;;
    --sleep-seconds)
      SLEEP_SECONDS="$2"
      shift 2
      ;;
    --counts)
      COUNTS_CSV="$2"
      shift 2
      ;;
    --queue)
      QUEUE_NAME="$2"
      shift 2
      ;;
    --lease-ttl)
      LEASE_TTL="$2"
      shift 2
      ;;
    --heartbeat)
      HEARTBEAT="$2"
      shift 2
      ;;
    --poll)
      POLL_INTERVAL="$2"
      shift 2
      ;;
    --timeout-seconds)
      WAIT_TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --no-build)
      BUILD_CLI=false
      shift
      ;;
    --results-file)
      RESULTS_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if ! [[ "$JOBS" =~ ^[0-9]+$ ]] || (( JOBS < 1 )); then
  echo "--jobs must be a positive integer" >&2
  exit 1
fi

if ! [[ "$WAIT_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || (( WAIT_TIMEOUT_SECONDS < 1 )); then
  echo "--timeout-seconds must be a positive integer" >&2
  exit 1
fi

if ! [[ "$SLEEP_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "--sleep-seconds must be numeric (for example: 1 or 0.5)" >&2
  exit 1
fi

IFS=',' read -r -a COUNTS <<< "$COUNTS_CSV"
if [[ "${#COUNTS[@]}" -eq 0 ]]; then
  echo "--counts cannot be empty" >&2
  exit 1
fi

max_count=0
for count in "${COUNTS[@]}"; do
  if ! [[ "$count" =~ ^[0-9]+$ ]] || (( count < 1 )); then
    echo "--counts must contain positive integers only" >&2
    exit 1
  fi
  if (( count > max_count )); then
    max_count="$count"
  fi
done

if [[ -z "${QUEUECTL_DB_POOL_SIZE:-}" ]]; then
  export QUEUECTL_DB_POOL_SIZE="$((max_count + 4))"
fi

if [[ -z "$RESULTS_FILE" ]]; then
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  RESULTS_FILE="$ROOT_DIR/data/concurrency-benchmark-${timestamp}.csv"
fi
mkdir -p "$(dirname "$RESULTS_FILE")"

worker_pid=""
ORIGINAL_ALLOWED_COMMANDS=""
cleanup() {
  if [[ -n "$worker_pid" ]] && kill -0 "$worker_pid" >/dev/null 2>&1; then
    "$BIN" worker stop >/dev/null 2>&1 || true
    wait "$worker_pid" >/dev/null 2>&1 || true
    worker_pid=""
  fi
  if [[ -n "$ORIGINAL_ALLOWED_COMMANDS" ]]; then
    set_config_value "allowed-commands" "$ORIGINAL_ALLOWED_COMMANDS" || true
  fi
}
trap cleanup EXIT INT TERM

now_ms() {
  python3 - <<'PY'
from time import time
print(int(time() * 1000))
PY
}

db_query_row() {
  local sql="$1"
  docker exec queuectl-postgres psql -U "$QUEUECTL_DB_USER" -d "$QUEUECTL_DB_NAME" -t -A -c "$sql" \
    | tr -d '[:space:]'
}

await_db() {
  local retries=60
  for _ in $(seq 1 "$retries"); do
    if docker exec queuectl-postgres pg_isready -U "$QUEUECTL_DB_USER" -d "$QUEUECTL_DB_NAME" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Postgres did not become ready in time" >&2
  exit 1
}

reset_db() {
  docker exec queuectl-postgres psql -U "$QUEUECTL_DB_USER" -d "$QUEUECTL_DB_NAME" -c \
    "TRUNCATE dlq, jobs, workers RESTART IDENTITY; UPDATE control SET value='false', updated_at=NOW() WHERE key='shutdown_requested';" \
    >/dev/null
}

set_config_value() {
  local key="$1"
  local value="$2"
  local escaped="${value//\'/\'\'}"
  docker exec queuectl-postgres psql -U "$QUEUECTL_DB_USER" -d "$QUEUECTL_DB_NAME" -c \
    "INSERT INTO config (key, value, updated_at) VALUES ('${key}', '${escaped}', NOW()) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at;" \
    >/dev/null
}

echo "[bench] starting postgres container"
docker compose up -d postgres >/dev/null
await_db

if [[ "$BUILD_CLI" == "true" ]]; then
  echo "[bench] building CLI distribution"
  ./gradlew :queuectl-cli:installDist >/dev/null
fi

echo "[bench] configuring queue settings"
ORIGINAL_ALLOWED_COMMANDS="$(db_query_row "SELECT value FROM config WHERE key='allowed-commands';")"
set_config_value "allowed-commands" "echo,sleep"

tmp_results="$(mktemp -t queuectl-bench.XXXXXX)"
printf "workers,elapsed_seconds,jobs_per_second,effective_concurrency,peak_active,completed,failed,dead,status\n" >"$tmp_results"

for count in "${COUNTS[@]}"; do
  echo "[bench] run workers=${count}, jobs=${JOBS}, sleep=${SLEEP_SECONDS}s"
  reset_db

  for i in $(seq 1 "$JOBS"); do
    "$BIN" enqueue --queue "$QUEUE_NAME" --command sleep --arg "$SLEEP_SECONDS" --max-retries 1 >/dev/null
  done

  start_ms="$(now_ms)"
  "$BIN" worker start \
    --count "$count" \
    --queues "$QUEUE_NAME" \
    --lease-ttl "$LEASE_TTL" \
    --heartbeat "$HEARTBEAT" \
    --poll "$POLL_INTERVAL" \
    --metrics-port 0 \
    >/tmp/queuectl-benchmark-worker-"$count".log 2>&1 &
  worker_pid=$!

  peak_active=0
  status="ok"
  completed=0
  failed=0
  dead=0
  while true; do
    counts_row="$(db_query_row "SELECT COALESCE(SUM((state='completed')::int),0), COALESCE(SUM((state='failed')::int),0), COALESCE(SUM((state='dead')::int),0), COALESCE(SUM((state='canceled')::int),0), COALESCE(SUM((state='processing')::int),0), COALESCE(SUM((state='leased')::int),0) FROM jobs;")"
    IFS='|' read -r completed failed dead canceled processing leased <<< "$counts_row"
    active="$((processing + leased))"
    terminal="$((completed + failed + dead + canceled))"
    if (( active > peak_active )); then
      peak_active="$active"
    fi
    if (( terminal >= JOBS )); then
      break
    fi
    elapsed_seconds="$((($(now_ms) - start_ms) / 1000))"
    if (( elapsed_seconds >= WAIT_TIMEOUT_SECONDS )); then
      status="timeout"
      break
    fi
    sleep 0.20
  done

  end_ms="$(now_ms)"
  "$BIN" worker stop >/dev/null || true
  wait "$worker_pid" >/dev/null 2>&1 || true
  worker_pid=""

  counts_row="$(db_query_row "SELECT COALESCE(SUM((state='completed')::int),0), COALESCE(SUM((state='failed')::int),0), COALESCE(SUM((state='dead')::int),0), COALESCE(SUM((state='processing')::int),0), COALESCE(SUM((state='leased')::int),0) FROM jobs;")"
  IFS='|' read -r completed failed dead processing leased <<< "$counts_row"
  active="$((processing + leased))"
  if (( active > peak_active )); then
    peak_active="$active"
  fi

  elapsed="$(awk "BEGIN {printf \"%.3f\", (${end_ms} - ${start_ms}) / 1000}")"
  throughput="$(awk "BEGIN {if (${elapsed} > 0) printf \"%.3f\", ${completed} / ${elapsed}; else print \"0.000\"}")"
  effective_concurrency="$(awk "BEGIN {printf \"%.3f\", ${throughput} * ${SLEEP_SECONDS}}")"

  printf "%s,%s,%s,%s,%s,%s,%s,%s,%s\n" \
    "$count" "$elapsed" "$throughput" "$effective_concurrency" "$peak_active" "$completed" "$failed" "$dead" "$status" \
    >>"$tmp_results"
done

cp "$tmp_results" "$RESULTS_FILE"

if [[ "$(wc -l < "$tmp_results")" -le 1 ]]; then
  echo "[bench] no completed runs recorded; check worker logs in /tmp/queuectl-benchmark-worker-*.log" >&2
  exit 1
fi

echo
echo "=== Concurrency Benchmark Summary ==="
awk -F',' '
  NR == 1 {
    printf "%-8s %-11s %-11s %-15s %-11s %-10s %-8s %-8s %-8s\n",
      "workers", "elapsed_s", "jobs_s", "effective_conc", "peak_active", "completed", "failed", "dead", "status";
    next
  }
  {
    printf "%-8s %-11s %-11s %-15s %-11s %-10s %-8s %-8s %-8s\n",
      $1, $2, $3, $4, $5, $6, $7, $8, $9
  }' "$tmp_results"

max_jobs_s="$(awk -F',' 'NR > 1 && $9 == "ok" && $7 == 0 && $8 == 0 { if ($3 > m) m = $3 } END { printf "%.6f", m + 0 }' "$tmp_results")"
best_workers="$(awk -F',' -v max="$max_jobs_s" 'NR > 1 && $9 == "ok" && $7 == 0 && $8 == 0 && $3 == max { print $1; exit }' "$tmp_results")"
recommended_workers="$(awk -F',' -v max="$max_jobs_s" 'NR > 1 && $9 == "ok" && $7 == 0 && $8 == 0 && $3 >= (0.95 * max) { print $1; exit }' "$tmp_results")"
max_peak_active="$(awk -F',' 'NR > 1 { if ($5 > m) m = $5 } END { print m + 0 }' "$tmp_results")"

echo
echo "[bench] db pool size: ${QUEUECTL_DB_POOL_SIZE}"
echo "[bench] max observed active jobs (processing+leased): ${max_peak_active}"
echo "[bench] best throughput: ${max_jobs_s} jobs/s at workers=${best_workers}"
if [[ -n "$recommended_workers" ]]; then
  echo "[bench] recommended worker count (>=95% of max throughput): ${recommended_workers}"
else
  echo "[bench] could not compute recommendation (check run statuses/failures)"
fi
echo "[bench] raw CSV saved at: ${RESULTS_FILE}"

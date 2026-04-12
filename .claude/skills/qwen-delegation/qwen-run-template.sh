#!/usr/bin/env bash
# Wrapper for qwen CLI that handles nvm setup transparently and provides observability.
# Usage: qwen-run [--timeout <seconds>] <task-name> [qwen args...]

TIMEOUT_SEC=300

if [[ "$1" == "--timeout" ]]; then
  TIMEOUT_SEC=$2
  shift 2
fi

TASK=$1
shift

if [ -z "$TASK" ]; then
  echo "Usage: qwen-run [--timeout <seconds>] <task-name> [qwen args...]"
  exit 1
fi

LOG_DIR="/tmp/logs"
mkdir -p "$LOG_DIR"

LOG_FILE="${LOG_DIR}/${TASK}.log"
PID_FILE="${LOG_DIR}/${TASK}.pid"

source "$HOME/.nvm/nvm.sh"
nvm use v22.20.0 --silent

echo "[$(date -u +%FT%TZ)] START task=${TASK} timeout=${TIMEOUT_SEC}s" | tee -a "$LOG_FILE"

echo $$ > "$PID_FILE"

timeout ${TIMEOUT_SEC} qwen "$@" 2>&1 | tee -a "$LOG_FILE"
EXIT_CODE=${PIPESTATUS[0]}

if [ $EXIT_CODE -eq 124 ]; then
  echo "[$(date -u +%FT%TZ)] TIMEOUT task=${TASK} (exceeded ${TIMEOUT_SEC}s)" | tee -a "$LOG_FILE"
else
  echo "[$(date -u +%FT%TZ)] END task=${TASK} exit_code=${EXIT_CODE}" | tee -a "$LOG_FILE"
fi

rm -f "$PID_FILE"
exit $EXIT_CODE

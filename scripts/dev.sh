#!/usr/bin/env bash
# scripts/dev.sh — orchestrate backend and frontend dev servers in parallel
# with merged stdout. Assumes the docker compose stack is already up
# (run `make up` first if it isn't).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.dev-logs"
mkdir -p "${LOG_DIR}"

BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"

BACKEND_PID=""
FRONTEND_PID=""
TAIL1_PID=""
TAIL2_PID=""

shutdown_children() {
    set +e
    for pid in "${BACKEND_PID}" "${FRONTEND_PID}" "${TAIL1_PID}" "${TAIL2_PID}"; do
        [[ -z "${pid}" ]] && continue
        kill -0 "${pid}" 2>/dev/null || continue
        pkill -P "${pid}" 2>/dev/null
        kill "${pid}" 2>/dev/null
    done
    sleep 0.3
    for pid in "${BACKEND_PID}" "${FRONTEND_PID}" "${TAIL1_PID}" "${TAIL2_PID}"; do
        [[ -z "${pid}" ]] && continue
        kill -0 "${pid}" 2>/dev/null && kill -9 "${pid}" 2>/dev/null
    done
    wait 2>/dev/null
}

on_signal() {
    local sig="$1"
    set +e
    printf '\n==> Received SIG%s, shutting down...\n' "${sig}" >&2
}

trap 'on_signal INT' INT
trap 'on_signal TERM' TERM
trap 'shutdown_children' EXIT

printf 'Backend  log: %s\n' "${BACKEND_LOG}"
printf 'Frontend log: %s\n' "${FRONTEND_LOG}"
printf 'Press Ctrl+C to stop both.\n\n'

(
    cd "${ROOT_DIR}"
    export SERVER_PORT="${SERVER_PORT:-8090}"
    exec backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun -x test
) >"${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!

(
    cd "${ROOT_DIR}/frontend"
    exec pnpm dev
) >"${FRONTEND_LOG}" 2>&1 &
FRONTEND_PID=$!

tail -F "${BACKEND_LOG}" 2>/dev/null | sed -u 's/^/[backend]  /' &
TAIL1_PID=$!
tail -F "${FRONTEND_LOG}" 2>/dev/null | sed -u 's/^/[frontend] /' &
TAIL2_PID=$!

set +e
wait -n "${BACKEND_PID}" "${FRONTEND_PID}"
WAIT_RC=$?
set -e

printf '\nA dev process exited (status %d). Last log lines:\n' "${WAIT_RC}" >&2
printf -- '--- backend (last 20 lines) ---\n' >&2
tail -n 20 "${BACKEND_LOG}" >&2 || true
printf -- '--- frontend (last 20 lines) ---\n' >&2
tail -n 20 "${FRONTEND_LOG}" >&2 || true

exit "${WAIT_RC}"
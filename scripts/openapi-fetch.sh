#!/usr/bin/env bash
# T419 — Shared OpenAPI spec fetch helper.
#
# Boots :starter-launcher:bootRun headless on SERVER_PORT (default 8090), waits
# for /actuator/health UP (bounded retries — no infinite loop), curls
# /v3/api-docs, normalizes (indent=2, sort_keys), asserts path count >=
# MIN_OPENAPI_PATHS, writes the result to the output path, then tears the JVM
# down with the same trap discipline as the inline target it replaces. NEVER
# leaves an orphan StarterApplication JVM.
#
# Usage: scripts/openapi-fetch.sh <outpath>
# Env:   SERVER_PORT (default 8090), MIN_OPENAPI_PATHS (required)
# Exit:  0 on success, non-zero with a clear message on any failure.

set -euo pipefail

if [ "$#" -ne 1 ]; then
    printf 'usage: %s <outpath>\n' "$0" >&2
    exit 2
fi

OUT="$1"
if [ -z "${MIN_OPENAPI_PATHS:-}" ]; then
    printf '[fail] MIN_OPENAPI_PATHS env var is required\n' >&2
    exit 2
fi

PORT="${SERVER_PORT:-8090}"
BASE="http://localhost:${PORT}"
LOG="$(mktemp /tmp/cbs-nova-openapi-boot.XXXXXX.log)"

cleanup() {
    pkill -f 'cbs.nova.starter.[S]tarterApplication' 2>/dev/null || true
    rm -f "$LOG"
}
trap cleanup EXIT INT TERM

printf '\n==> Booting starter-launcher headless on port %s...\n' "$PORT"
env SERVER_PORT="$PORT" \
    backend/dsl-platform/gradlew -p backend/dsl-starter \
    :starter-launcher:bootRun --console=plain >"$LOG" 2>&1 &

up=0
for i in $(seq 1 40); do
    if curl -sf --max-time 2 "$BASE/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        up=1
        break
    fi
    sleep 3
done

if [ "$up" -ne 1 ]; then
    printf '    [fail] backend not healthy after ~120s (log: %s)\n' "$LOG" >&2
    tail -20 "$LOG" >&2
    exit 1
fi
printf '    [ok]   backend healthy (%s/actuator/health)\n' "$BASE"

# /v3/api-docs can occasionally come back with no Content-Type on the first
# call after the actuator comes up; --fail-with-body still lets us see why.
raw="$(mktemp /tmp/cbs-nova-openapi-raw.XXXXXX.json)"
trap 'rm -f "$raw"; cleanup' EXIT INT TERM

if ! curl -sf --max-time 30 "$BASE/v3/api-docs" -o "$raw"; then
    printf '    [fail] curl /v3/api-docs failed (see %s)\n' "$LOG" >&2
    tail -20 "$LOG" >&2
    exit 1
fi

python3 -c \
    'import json,sys; print(json.dumps(json.load(sys.stdin), indent=2, sort_keys=True))' \
    < "$raw" > "$OUT"

rm -f "$raw"

python3 -c \
    'import json,sys; d=json.load(open(sys.argv[1])); n=len(d.get("paths",{})); assert n>=int(sys.argv[2]), f"paths count {n} < {sys.argv[2]}"; print(f"    [ok]   {n} paths (>= {sys.argv[2]})")' \
    "$OUT" "$MIN_OPENAPI_PATHS"

printf '    [ok]   wrote %s\n' "$OUT"
printf '==> Shutting down backend...\n'
pkill -f 'cbs.nova.starter.[S]tarterApplication' 2>/dev/null || true
for i in $(seq 1 15); do
    curl -s --max-time 1 "$BASE/" >/dev/null 2>&1 || break
    sleep 1
done
if curl -s --max-time 1 "$BASE/" >/dev/null 2>&1; then
    printf '    [fail] backend still serving on %s\n' "$BASE" >&2
    exit 1
fi

trap - EXIT INT TERM
rm -f "$LOG"
printf '    [ok]   backend stopped, fetch complete\n'

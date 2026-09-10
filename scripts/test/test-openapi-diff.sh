#!/usr/bin/env bash
# T419 — classifier self-test. Exits 0 on success, non-zero on the first failed case.
# Run from repo root:  bash scripts/test/test-openapi-diff.sh

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIX="$ROOT/scripts/test/fixtures"
DIFF="$ROOT/scripts/openapi-diff.py"

if [ ! -x "$DIFF" ] && ! command -v python3 >/dev/null 2>&1; then
    echo "[fail] python3 required" >&2
    exit 1
fi

fails=0

run_case() {
    local name="$1" old="$2" new="$3" want_exit="$4"
    got_exit=0
    python3 "$DIFF" "$old" "$new" >/dev/null 2>&1 || got_exit=$?
    if [ "$got_exit" -eq "$want_exit" ]; then
        printf '    [ok]   %s (exit=%s)\n' "$name" "$got_exit"
    else
        printf '    [fail] %s (got exit=%s, want %s)\n' "$name" "$got_exit" "$want_exit"
        fails=$((fails+1))
    fi
}

run_case "additive only -> exit 0" \
    "$FIX/old-additive.json" "$FIX/new-additive.json" 0

run_case "breaking + same info.version -> exit 2" \
    "$FIX/old-breaking.json" "$FIX/new-breaking-same-version.json" 2

run_case "breaking + bumped info.version -> exit 0" \
    "$FIX/old-breaking.json" "$FIX/new-breaking-bumped-version.json" 0

if [ "$fails" -gt 0 ]; then
    printf '\n%d classifier case(s) failed.\n' "$fails"
    exit 1
fi
printf '\nAll classifier self-test cases passed.\n'

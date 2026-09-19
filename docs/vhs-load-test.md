# VHS Load Test

Run concurrent VHS tape replays at scale, report latency percentiles and error rates,
and publish Micrometer metrics via the existing starter registry.

## Quick start

```bash
# Dry-run load test (default, safe — no real calls)
python scripts/cbs_cli.py vhs loadtest \
  --tapes "backend/dsl-starter/starter/src/test/resources/vhs/*.vhs.jsonl" \
  --target dry-run --speed 2.0 --concurrency 4

# Local target (hits the real local backend)
CBS_VHS_REPLAY_ALLOW_PRODUCTION=1 python scripts/cbs_cli.py vhs loadtest \
  --tapes "/path/to/recorded-tapes/*.vhs.jsonl" \
  --target local --speed 1.0 --concurrency 10 --duration 30
```

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `TAPES_GLOB` | `**/*.vhs.jsonl` | Glob pattern for tape files (CLI `--tapes` overrides) |
| `CBS_VHS_REPLAY_TARGET` | `dry-run` | Replay target |
| `CBS_VHS_REPLAY_SPEED` | `1.0` | Speed multiplier |
| `CBS_VHS_REPLAY_CONCURRENCY` | `4` | Concurrency cap |
| `CBS_VHS_REPLAY_DURATION` | `0` | Duration cap in seconds (0 = run all copies) |
| `CBS_VHS_REPLAY_ALLOW_PRODUCTION` | — | Must be `1` + config key for non-dry-run/non-local targets |

## Production safety

The load-test CLI enforces the same two-key production guard as T557/T559:

- **Default:** dry-run (no side effects)
- **Reject** any target other than `dry-run` or `local` unless **both**:
  1. `cbs.vhs.replay.allow-production=true` in Spring config
  2. `CBS_VHS_REPLAY_ALLOW_PRODUCTION=1` environment variable

A loud startup warning summarizes target, concurrency, and speed before any tape is read.

## Micrometer meters

Published to the existing starter `MeterRegistry` — no separate metrics path:

- **Timer:** `dsl.vhs.replay.duration` tags: `target`, `tape`, `process`
- **Counter:** `dsl.vhs.replay.calls` tags: `target`, `tape`, `process`, `status` (`success`|`error`)

Percentiles (p50/p95/p99) are published as Timer distribution percentiles.

## CLI output

The `vhs loadtest` subcommand prints a per-tape summary table:

```
tape                     calls  succ  fail  p50     p95     p99     err%    total
-----------------------  -----  ----  ----  ------  ------  ------  ------  ------
tape_a.vhs.jsonl#0            2     2     0  100ms   100ms   100ms   0.0%        2
```

## API endpoint

```
POST /api/v1/vhs/loadtest
Content-Type: application/json

{
  "tapes": "**/*.vhs.jsonl",
  "target": "dry-run",
  "speed": 2.0,
  "concurrency": 4,
  "duration": 0
}
```

Returns `VhsLoadTestReport` JSON with `tapeSummaries` (per-tape percentiles) and overall stats.

## Open question: CI perf-gate thresholds

Whether to wire this load test into CI and what latency/error thresholds to enforce is
**intentionally left as an open question** for the team that owns CI perf budgets.

This task does NOT add a CI workflow or hardcode any perf thresholds. The load test is a
local developer tool and API endpoint. CI integration should be a separate decision once
the team agrees on acceptable p99 and error-rate budgets.

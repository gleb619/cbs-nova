# T636 — Loadtest: executions list, stats, timeseries endpoints

## Goal

Extend `scripts/src/cbs_cli/commands/loadtest.py` (and its report) to cover the dashboard-facing
read endpoints: `/api/v1/executions`, `/api/v1/executions/stats`,
`/api/v1/executions/stats/timeseries`. Keep existing endpoints (definitions, drafts, helpers)
untouched.

## Why

`make loadtest` measures only 3 low-frequency endpoints. The admin UI's watched surfaces —
executions list page, dashboard stats, trend charts — hit executions endpoints on every visit
and poll; their latency under load is unmeasured. Seeded history (`make seed-history`) makes
these endpoints return realistic payloads today.

## Acceptance criteria

- [ ] Three endpoints added to the loadtest rotation with per-endpoint percentiles + error rate
      in the existing report format.
- [ ] Timeseries called with a representative window query param (match what the UI sends).
- [ ] Report table lists all endpoints; p50/p95/p99 + error rate per endpoint.
- [ ] Verified against a running stack with seeded history (empty-DB behavior: still reports,
      zero rows — no crash).
- [ ] Extend/adjust loadtest tests if any exist (pytest under `scripts/test/`); `make lint`
      passes.

## Tier

`backend`

## Files to create/modify

- Modify: `scripts/src/cbs_cli/commands/loadtest.py`
- Possibly: its report renderer + `scripts/test/` coverage.

## Build/test commands

```bash
make seed-history && make loadtest
python3 -m pytest scripts/test -q
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Read-only endpoints only — no write/load mutation endpoints in loadtest.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

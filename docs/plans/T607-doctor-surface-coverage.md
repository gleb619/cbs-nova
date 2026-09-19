# T607 — Extend `make doctor` with shipped BFF surface checks

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

`scripts/src/cbs_cli/commands/doctor.py` (87 lines) smoke-checks: Keycloak, Gitea, Temporal,
BFF up, `dsl/helpers`, `dsl/definitions`. Many shipped surfaces are untracked:

| Surface | BFF route |
|---|---|
| Executions list | `/api/v1/executions` |
| Diagnostics log | `/api/v1/dsl/diagnostics` |
| Domain events | `/api/v1/dsl/events` |
| Audit log | `/api/v1/dsl/audit` |
| Webhook deliveries | `/api/v1/dsl/webhooks/deliveries` |
| Schedules | `/api/v1/dsl/schedules` |
| Health / info | `/api/v1/health`, `/api/v1/info` |

Add status-code checks for these (200-ish = ok, else fail with the code), same
`Printer.ok/fail` style, after the existing definitions check. Keep the report compact — one
line per surface.

## Tier

`backend` (python CLI under `scripts/`)

## Acceptance criteria

- [ ] All seven surfaces above get a doctor check line; existing checks untouched.
- [ ] Checks tolerate surfaces that are conditional in some deployments (e.g. schedules
      Temporal-gated) — a non-200 that is a clean 404 from the BFF should report `SKIP`/`warn`,
      not full `FAIL` (match how the script currently grades partial availability; if no warn
      level exists, add a `Printer.warn`-equivalent minimal line).
- [ ] New pytest coverage under `scripts/test/` for the added check logic (mock `Curl`; follow
      `test_classify.py` style). At minimum: 200 → ok, 404 → warn/skip, connection-refused →
      fail.
- [ ] `python3 -m pytest scripts/test/` green (existing tests unaffected).
- [ ] `make doctor` still runs against a live stack without new errors (manual verify if stack
      up; otherwise state skipped).
- [ ] `make lint` green.

## Out of scope

- Authenticated surfaces (skip anything needing a token unless the existing script already
  handles auth).
- VHS / manifest / drafts panels — follow-up if wanted.

## Files to create/modify

- `scripts/src/cbs_cli/commands/doctor.py` (modify)
- `scripts/test/test_doctor.py` (create)

## Build/test commands

```bash
python3 -m pytest scripts/test/
make doctor   # with stack up
make lint
```

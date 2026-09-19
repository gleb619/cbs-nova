# T614 — Runbook recipes: approval-gated publish + sending signals

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Two shipped operator flows have zero runbook coverage (grep: 0 hits for approval/change-request,
0 for signal):

1. **Approval-gated publish (T568)** — `cbs.dsl.approval.required=true` two-person rule:
   `POST /api/v1/dsl/drafts/{name}/change-request` (AUTHOR snapshots draft, supersedes prior
   PENDING), `GET /api/v1/dsl/change-requests?definitionName=&status=`,
   `POST /api/v1/dsl/change-requests/{id}/approve` / `reject` (self-approval blocked, ADMIN
   exempt; audit entries on every action; migration `V11__change_requests.sql`).
2. **Signals (T567)** — `POST /api/v1/dsl/signals/{runId}` (sendSignal to a running process
   that declared the signal), `GET /api/v1/dsl/queries/{runId}` (querySignalState). Requires
   the process to declare/await the signal (`SignalProbeDsl` example shows the DSL side).

Add two sections following the "Schedule a definition" recipe style: when to use, prerequisites
(roles / process declaration), exact curl against `:3000` BFF with realistic request/response
envelopes, failure modes (403 below-OPERATOR direct publish, self-approve rejection, signal to
undeclared name, unknown runId). Verify every envelope against source (`DslDraftHandler`,
`DslSignalsHandler`, models) — no invented fields.

## Tier

`backend` (docs-only)

## Acceptance criteria

- [ ] Section "Publish via the approval gate": full round-trip curl (create CR → list → approve
      → publish happens) + reject path + direct-publish 403 case.
- [ ] Section "Signal a running process": declare-side prerequisite (one line + pointer to
      `SignalProbeDsl`), send curl, state query curl, RBAC notes from `RbacAuthorizationFilter`.
- [ ] All routes/fields verified against handler + model source; envelope examples realistic.
- [ ] Cross-reference from the two new sections to the T568 paragraph in
      `docs/architecture-backend.md` (~line 205) and back (one line each).
- [ ] `make lint` green.

## Out of scope

- T611's signals architecture section (this task is operator recipes only).
- Promotion UI (T569, still in flight).

## Files to create/modify

- `docs/runbook.md` (modify — two sections)
- `docs/architecture-backend.md` (one back-reference line, optional)

## Build/test commands

```bash
make lint
```

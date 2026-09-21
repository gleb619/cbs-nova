# T644 — ChangeRequestHandler spec

## Goal

Add unit spec for `ChangeRequestHandler` (148 LOC) — submit/list/approve/reject HTTP shell
around `ChangeRequestService` (service is tested; the HTTP layer is not).

## Why

Approval gate (T568) is operator + RBAC facing: `RoleResolver` picks the caller role, and the
404 (no draft) / role-dependent approve paths exist only at the handler layer. Zero test refs.
Runbook recipe T614 will document 403/self-approve flows — pin them in tests first.

## Acceptance criteria

- [ ] `submit` — 201 envelope; 404 when no draft exists for the definition.
- [ ] `list` — filters (definitionName, status) mapped to the service; newest-first passthrough.
- [ ] `approve` / `reject` — role resolution via shared `RoleResolver`; self-approve rejection
      path (if enforced here — pin whatever the handler does); not-found id.
- [ ] Service mocked; style follows sibling handler specs (e.g. `ApiKeyAdminHandlerTest`).
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/controller/ChangeRequestHandlerTest.java`
- Read first: `ChangeRequestHandler.java`, `ChangeRequestServiceTest` for domain fixtures,
      `RoleResolver` contract.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.controller.ChangeRequestHandlerTest'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no handler changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

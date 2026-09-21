# T643 — NotificationRuleHandler spec

## Goal

Add unit spec for `NotificationRuleHandler` (264 LOC) — admin CRUD API for notification rules
(list/create/update/delete, whatever routes it exposes). Zero test references today.

## Why

Controller package: most handlers have specs (ApiKeyAdmin, DslAudit, DslSchedule, Webhook, …) but
`NotificationRuleHandler` has none — neither direct nor indirect. Engine/repo have integration
tests; the HTTP surface (envelopes, validation errors, status codes) is unpinned.

## Acceptance criteria

- [ ] CRUD routes covered: happy-path envelope shapes + status codes.
- [ ] Validation failure path: malformed rule payload → error envelope pinned.
- [ ] Not-found path for id-addressed operations.
- [ ] Style follows sibling handler specs (e.g. `ApiKeyAdminHandlerTest`).
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/controller/NotificationRuleHandlerTest.java`
- Read first: `NotificationRuleHandler.java`, `NotificationRuleEngineIntegrationTest` for
      domain fixtures, sibling handler spec for style.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.controller.NotificationRuleHandlerTest'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no handler changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

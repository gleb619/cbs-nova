# T627 — DslPromoteHandler spec

## Goal

Add unit spec for `DslPromoteHandler` (230 LOC) — the backend handler behind environment
promotion (list envs/definitions, dry-run, apply), with promote logic living directly in the
handler (no service layer).

## Why

T569 shipped the promotion workflow (UI + runbook recipe T616 queued) but the 230 LOC handler has
zero test coverage. Promotion is an operator action touching multiple environments — dry-run /
apply divergences or envelope regressions would surface only in production promotion attempts.

## Acceptance criteria

- [ ] List environments: envelope shape, error mapping.
- [ ] List definitions per env.
- [ ] Dry-run: diff payload mapped correctly; failure envelope pinned.
- [ ] Apply: success + failure paths, characterization style.
- [ ] Test style follows sibling handler specs (e.g. `DslScheduleHandlerTest`,
      `ApiKeyAdminHandlerTest`) — same mocking/fake approach for downstream calls.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/controller/DslPromoteHandlerTest.java`
- Read first: `DslPromoteHandler.java`, sibling handler test for fixtures.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.controller.DslPromoteHandlerTest'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no handler changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

# T622 — Delete deprecated ExplainReport.withChildren/addChild

## Goal

Remove `ExplainReport.withChildren(List)` and `ExplainReport.addChild(ExplainReport)` — both
`@Deprecated(forRemoval = true)`, both used only by `ExplainReportTest` — and migrate those tests
to the builder. Delete the two `//TODO: add usage in source code, not only in test ones` markers.

## Why

`ExplainReport.java:47,58` TODOs flag that these methods have no source-code usage. Zero main-code
call sites confirmed (grep across dsl-platform/dsl-starter/dsl-plugins). Dead public API on a
record meant to be pure data — construction belongs to the builder.

## Acceptance criteria

- [ ] Both methods deleted from `ExplainReport` along with their TODO comments.
- [ ] `ExplainReportTest` rewritten to builder-based construction; same behavioral assertions kept
      (immutability, shared-child safety, append semantics where still meaningful).
- [ ] Grep confirms no remaining `withChildren(`/`addChild(` references outside `ExplainReport`.
- [ ] Full dsl-api test suite green.
- [ ] `make lint` passes.

## Tier

`backend`

## Files to create/modify

- Modify: `backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/model/ExplainReport.java`
- Modify: `backend/dsl-platform/dsl-api/src/test/java/cbs/nova/dsl/model/ExplainReportTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-api:test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Pure deletion + test migration — no new API added.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

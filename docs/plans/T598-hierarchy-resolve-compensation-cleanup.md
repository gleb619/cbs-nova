# T598 — Verify/retire `HierarchyReportStage.resolveCompensation` dead branching

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

`HierarchyReportStage.resolveCompensation`
(`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/stage/HierarchyReportStage.java:81`,
`@Deprecated(forRemoval = true)`) carries the same TODO as its sibling covered by T585:

```
// TODO: now objects alwasys have a compensations. Fallback is NoOp impl, so compensation is
// nonnull from now
```

implying the `compensationLogic != null` check it performs (via `GlobalManager.findProcess` /
`findTransaction`) is now always true and the method can collapse to `return true` (or be inlined
at the single call site, line 48).

Sibling task T585 covers `ExplainReportStage.resolveCompensation`; this task covers the
Hierarchy twin. Verify the premise (NoOp fallback makes `compensationLogic` nonnull) before
removing — reuse whatever evidence T585's execution produced; if T585 is still Backlog, apply the
same verification steps here.

## Tier

`backend`

## Acceptance criteria

- [ ] Premise verified: for both `PROCESS` and `TRANSACTION`, builders never leave
      `compensationLogic` null (NoOp fallback) — cite the builder code lines that prove it.
- [ ] Dead branching removed: method deleted or collapsed, call site simplified.
- [ ] Existing Hierarchy report tests still pass; add/extend one test asserting
      `hasCompensation == true` for a definition without explicit compensation logic.
- [ ] `backend/dsl-starter/gradlew -p backend/dsl-starter :starter:test` green.
- [ ] `make lint` green.
- [ ] TODO comment removed with the code.

## Out of scope

- `ExplainReportStage.resolveCompensation` (T585).
- Any change to `ProcessBuilder`/`TransactionBuilder` compensation wiring itself.

## Files to create/modify

- `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/stage/HierarchyReportStage.java` (modify)
- Related stage test(s) under `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/core/stage/` (modify)

## Build/test commands

```bash
backend/dsl-starter/gradlew -p backend/dsl-starter :starter:test --tests '*HierarchyReportStage*'
backend/dsl-starter/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Blocked — duplicate of T585 (2026-09-22)

T585's plan file mis-named the target as `ExplainReportStage.resolveCompensation`, but that method
doesn't exist — the actual TODO/`@Deprecated` code T585 fixed (commit `dea01676`) is exactly
`HierarchyReportStage.resolveCompensation` (this file, line ~81). T585 already verified the premise
false (no NoOp fallback anywhere in `ProcessBuilder`/`TransactionBuilder`/runners), kept the null
check as load-bearing, rewrote the comment to state the real invariant, and removed
`@Deprecated(forRemoval=true)`. Current source already matches this task's acceptance criteria.
Flagging Blocked instead of silently closing — human should confirm and archive/delete this row
rather than have the loop re-do already-shipped work.

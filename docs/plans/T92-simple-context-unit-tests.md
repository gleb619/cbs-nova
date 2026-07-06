# T92 — SimpleContext Unit Tests

## Goal
Add focused JUnit tests for `SimpleContext` so the base `Context<T>` implementation and runId/immutability semantics are locked.

## Acceptance Criteria
- `SimpleContext.of(body, mode)` creates a context with empty metadata and a generated runId.
- `SimpleContext.of(body, mode, runId)` preserves the supplied runId.
- `SimpleContext.of(body, metadata, mode, runId)` preserves metadata and runId.
- `withBody()` returns a new context with the same metadata, mode, and runId.
- `withMetadata()` returns a new context with an added/updated key while leaving the original immutable.
- `generateRunId()` produces a non-null, non-empty string starting with `run-`.

## Tier
`backend`

## Files to Create / Modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/SimpleContextTest.java`
- No production code changes expected.

## Build / Test Commands
Run from `backend/`:

```bash
./gradlew spotlessApply
./gradlew build test
```

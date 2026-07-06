# T82 — GlobalManager runtime gap tests

## Goal
Extend `GlobalManagerTest` to cover transaction and function round-trips, unknown transaction/function failures, and sorted name lists. Fills the remaining facade runtime coverage gaps without adding infrastructure.

## Tier
backend

## Files to create / modify
- Modify: `backend/dsl/src/test/java/cbs/nova/dsl/GlobalManagerTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/GlobalManager.java`

## Acceptance criteria
- `runTransaction` succeeds for a registered transaction and returns a failure for an unknown name.
- `runFunction` succeeds for a registered function and returns a failure for an unknown name.
- `processNames`, `transactionNames`, and `helperNames` return sorted lists.
- `describeHelper` returns a descriptor for a registered `@Helper`/Executable.
- Existing `GlobalManagerTest` cases continue to pass.
- `./gradlew :dsl:test --tests '*GlobalManagerTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test --tests '*GlobalManagerTest*'
```

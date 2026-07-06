# T78 — HelperManager unit tests

## Goal
Add focused unit tests for `HelperManager` and `DefaultHelperRunner` covering helper/function registration, name lookup, missing-name failure paths, and preview vs execute dispatch. Fills a runtime coverage gap with zero infrastructure.

## Tier
backend

## Files to create / modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/HelperManagerTest.java`
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/DefaultHelperRunnerTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/HelperManager.java`, `backend/dsl/src/main/java/cbs/nova/dsl/DefaultHelperRunner.java`

## Acceptance criteria
- `HelperManager` rejects duplicate helper/function registrations.
- `HelperManager.names()` returns sorted union of helpers and functions.
- `HelperManager.executeHelper`/`executeFunction` return failure for unknown names.
- `DefaultHelperRunner` delegates to `Executable.preview` in `PREVIEW` mode and `execute` otherwise.
- `DefaultHelperRunner` returns `DslExecutionException` on thrown errors, preserving `runId`.
- `./gradlew :dsl:test --tests '*HelperManagerTest*' --tests '*DefaultHelperRunnerTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test --tests '*HelperManagerTest*' --tests '*DefaultHelperRunnerTest*'
```

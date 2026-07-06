# T85 — Transaction runner explain-mode tests

## Goal
Add focused tests for `DefaultTransactionRunner` covering `EXPLAIN` mode metadata enrichment and exception-to-`DslExecutionException` wrapping. Complements T72/T75 by locking the transaction execution path independently.

## Tier
backend

## Files to create / modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/DefaultTransactionRunnerTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/DefaultTransactionRunner.java`, `backend/dsl/src/main/java/cbs/nova/dsl/TransactionDslObject.java`

## Acceptance criteria
- `run` in `EXPLAIN` mode applies logic and writes `explain.description` metadata on success.
- `run` in `RUN`/`PREVIEW` mode executes logic without metadata side effects.
- Thrown exception is converted to `DslExecutionException` preserving `runId`.
- `./gradlew :dsl:test --tests '*DefaultTransactionRunnerTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test --tests '*DefaultTransactionRunnerTest*'
```

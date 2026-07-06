# T101 — CompensationRichContext unit tests

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

Add focused unit tests for the DSL compensation runtime. `CompensationContext` / `CompensationRichContext` currently have no direct test coverage (confirmed by codegraph), yet they are on the critical path for every saga/rollback DSL example. Close the gap without changing production behavior.

## Acceptance Criteria

- [ ] `backend/dsl/src/test/java/cbs/nova/dsl/CompensationRichContextTest.java` created.
- [ ] Tests prove `CompensationRichContext` delegates `body()`, `metadata()`, `mode()`, `runId()`, `withBody()`, `withMetadata()` to the wrapped context unchanged.
- [ ] Tests prove `error()` returns the failure passed at construction.
- [ ] Tests prove `runHelper(name)` and `runHelper(name, input)` invoke `GlobalManager.runHelper(...)` with the correct context/runId and add an execution trace entry.
- [ ] Tests prove `log(message)` adds a trace entry and returns the same context (fluent).
- [ ] Tests prove `DefaultProcessRunner` emits a `DslCompensationException` when the compensation logic itself throws, and still returns the original failure when compensation succeeds.
- [ ] `./gradlew spotlessApply && ./gradlew :dsl:test` passes.

## Files to Create / Modify

- `backend/dsl/src/test/java/cbs/nova/dsl/CompensationRichContextTest.java` — new.
- `backend/dsl/src/test/java/cbs/nova/dsl/DefaultProcessRunnerCompensationTest.java` — new (or extend `RunnerTest.java` if simpler).

## Build / Test Commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test
```

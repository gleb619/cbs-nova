# T99 — DefaultProcessRegistry Unit Tests

## Goal
Add focused JUnit tests for `DefaultProcessRegistry` so process registry semantics used by `ProcessManager` are locked.

## Acceptance Criteria
- `register(process)` stores a process and `find(name)` returns it.
- `find(unknown)` returns `Optional.empty()`.
- Duplicate registration throws `IllegalArgumentException`.
- `all()` returns the collection of registered processes.

## Tier
`backend`

## Files to Create / Modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/DefaultProcessRegistryTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/DefaultProcessRegistry.java`, `backend/dsl/src/main/java/cbs/nova/dsl/ProcessDslObject.java`

## Build / Test Commands
Run from `backend/`:

```bash
./gradlew spotlessApply
./gradlew :dsl:test --tests '*DefaultProcessRegistryTest*'
```

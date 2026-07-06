# T72 — Transaction registry and manager tests

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

`DefaultTransactionRegistry` and `TransactionManager` currently have no direct test coverage (codegraph confirmed). They are on the execution path for every DSL transaction. Add focused unit tests for registry semantics and manager dispatch without changing production behavior.

## Acceptance Criteria

- [ ] `backend/dsl/src/test/java/cbs/nova/dsl/TransactionRegistryTest.java` created.
- [ ] Tests prove `DefaultTransactionRegistry` stores, finds, lists, and rejects duplicate transactions.
- [ ] `backend/dsl/src/test/java/cbs/nova/dsl/TransactionManagerTest.java` created.
- [ ] Tests prove `TransactionManager.execute` runs the configured runner for a registered transaction and returns `DslEntityNotFoundException` for unknown names.
- [ ] Tests prove `TransactionManager.contains`/`find`/`names` delegate to the registry.
- [ ] `./gradlew spotlessApply && ./gradlew :dsl:test` passes.

## Files to Create / Modify

- `backend/dsl/src/test/java/cbs/nova/dsl/TransactionRegistryTest.java` — new.
- `backend/dsl/src/test/java/cbs/nova/dsl/TransactionManagerTest.java` — new.

## Build / Test Commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test
```

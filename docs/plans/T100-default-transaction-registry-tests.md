# T100 — DefaultTransactionRegistry Unit Tests

## Goal
Add focused JUnit tests for `DefaultTransactionRegistry` so transaction registry semantics used by `TransactionManager` are locked.

## Acceptance Criteria
- `register(transaction)` stores a transaction and `find(name)` returns it.
- `find(unknown)` returns `Optional.empty()`.
- Duplicate registration throws `IllegalArgumentException`.
- `all()` returns the collection of registered transactions.

## Tier
`backend`

## Files to Create / Modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/DefaultTransactionRegistryTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/DefaultTransactionRegistry.java`, `backend/dsl/src/main/java/cbs/nova/dsl/TransactionDslObject.java`

## Build / Test Commands
Run from `backend/`:

```bash
./gradlew spotlessApply
./gradlew :dsl:test --tests '*DefaultTransactionRegistryTest*'
```

# T95 — DefaultParameterRegistry Unit Tests

## Goal
Add focused JUnit tests for `DefaultParameterRegistry` so the parameter descriptor collection used by `ProcessBuilder.parameters()` and `TransactionBuilder.parameters()` is locked.

## Acceptance Criteria
- `string(name)` adds a `STRING` descriptor with the given name.
- `number(name)` adds a `NUMBER` descriptor with the given name.
- `bool(name)` adds a `BOOLEAN` descriptor with the given name.
- `object(name, type)` adds an `OBJECT` descriptor with the given name and type.
- Descriptors preserve insertion order.
- `descriptors()` returns an unmodifiable list.
- Repeated calls accumulate descriptors.

## Tier
`backend`

## Files to Create / Modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/DefaultParameterRegistryTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/DefaultParameterRegistry.java`, `backend/dsl-api/src/main/java/cbs/nova/dsl/ParameterDescriptor.java`, `backend/dsl-api/src/main/java/cbs/nova/dsl/ParameterType.java`

## Build / Test Commands
Run from `backend/`:

```bash
./gradlew spotlessApply
./gradlew :dsl:test --tests '*DefaultParameterRegistryTest*'
```

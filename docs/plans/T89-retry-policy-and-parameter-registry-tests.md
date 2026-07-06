# T89 — RetryPolicy and ParameterRegistry unit tests

## Goal
Add focused unit tests for `RetryPolicy` and `DefaultParameterRegistry` covering default values, custom construction, and parameter descriptor collection. These are small, stable DSL building blocks currently lacking direct coverage.

## Tier
backend

## Files to create / modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/RetryPolicyTest.java`
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/DefaultParameterRegistryTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/RetryPolicy.java`, `backend/dsl/src/main/java/cbs/nova/dsl/DefaultParameterRegistry.java`, `backend/dsl-api/src/main/java/cbs/nova/dsl/ParameterDescriptor.java`

## Acceptance criteria
- `RetryPolicy.defaults()` returns expected `maxAttempts`, `initialInterval`, and `backoffCoefficient`.
- Custom `RetryPolicy` preserves provided values.
- `DefaultParameterRegistry` collects descriptors for `string`, `number`, `bool`, and `object` in insertion order.
- `descriptors()` returns an unmodifiable list.
- `./gradlew :dsl:test --tests '*RetryPolicyTest*' --tests '*DefaultParameterRegistryTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test --tests '*RetryPolicyTest*' --tests '*DefaultParameterRegistryTest*'
```

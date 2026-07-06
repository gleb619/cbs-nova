# T97 — DefaultHelperRegistry unit tests

## Goal
Add focused unit tests for `DefaultHelperRegistry` covering helper and function registration, cross-type duplicate rejection, `containsName`, `findHelper`, `findFunction`, and `allNames` semantics. Fills the remaining registry coverage gap left by `RegistryTest` which only exercises `DefaultProcessRegistry` and cross-type rejection.

## Tier
backend

## Files to create / modify
- Modify: `backend/dsl/src/test/java/cbs/nova/dsl/RegistryTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/DefaultHelperRegistry.java`

## Acceptance criteria
- Helper registration succeeds and `findHelper` returns it.
- Function registration succeeds and `findFunction` returns it.
- Registering a helper with a name already used by a function throws `IllegalArgumentException`.
- `containsName` is true for both helpers and functions.
- `allNames` returns the union of helper and function names.
- `./gradlew :dsl:test --tests '*RegistryTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test --tests '*RegistryTest*'
```

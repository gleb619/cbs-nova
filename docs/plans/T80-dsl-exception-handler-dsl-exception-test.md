# T80 — DslExceptionHandler DslException branch test

## Goal
Close the coverage gap in `DslExceptionHandler` by adding a MockMvc test for the `DslException` branch: a controller that throws a `DslException` should produce a 422 `ErrorResponse` containing the DSL error code, message, runId, and exceptionId. The existing handler test already covers `IllegalArgumentException` and generic `Exception` branches.

## Tier
backend

## Files to create / modify
- Modify: `backend/starter/src/test/java/cbs/nova/starter/DslExceptionHandlerTest.java`
- Read only: `backend/starter/src/main/java/cbs/nova/starter/DslExceptionHandler.java`, `backend/dsl-api/src/main/java/cbs/nova/dsl/DslException.java`

## Acceptance criteria
- Add endpoint `/throw/dsl` to `ThrowingController` that throws a `DslException`.
- New test `dslExceptionMapsTo422WithErrorResponse` asserts 422, `code`, `message`, `runId`, and `exceptionId`.
- Existing handler tests continue to pass.
- `./gradlew :starter:test --tests '*DslExceptionHandlerTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*DslExceptionHandlerTest*'
```

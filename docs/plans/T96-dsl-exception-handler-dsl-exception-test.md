# T96 — DslExceptionHandler DslException Branch Test

## Goal
Add a MockMvc test for the `DslException` branch of `DslExceptionHandler`, producing a 422 `ErrorResponse` with `code`, `message`, `runId`, and `exceptionId`. Existing tests already cover `IllegalArgumentException` and generic `Exception` branches.

## Acceptance Criteria
- Extend `ThrowingController` with a `/throw/dsl` endpoint that throws a `DslException`.
- New test asserts HTTP 422 Unprocessable Entity.
- Response body contains `code` matching the `DslErrorCode` name.
- Response body contains the original `message`, `runId`, and a non-empty `exceptionId`.
- Existing `DslExceptionHandlerTest` tests still pass.

## Tier
`backend`

## Files to Create / Modify
- Modify: `backend/starter/src/test/java/cbs/nova/starter/DslExceptionHandlerTest.java`
- Read only: `backend/starter/src/main/java/cbs/nova/starter/DslExceptionHandler.java`, `backend/dsl-api/src/main/java/cbs/nova/dsl/DslException.java`, `backend/dsl-api/src/main/java/cbs/nova/dsl/DslErrorCode.java`

## Build / Test Commands
Run from `backend/`:

```bash
./gradlew spotlessApply
./gradlew :starter:test --tests '*DslExceptionHandlerTest*'
```

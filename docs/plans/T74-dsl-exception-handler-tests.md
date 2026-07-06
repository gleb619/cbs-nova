# T74 — DSL exception handler tests

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

`DslExceptionHandler` currently has tests only for `RuntimeException` and `IllegalArgumentException` branches (`DslExceptionHandlerTest`). The `DslException` branch — which captures runId/exceptionId/code and maps to 422 — is untested. Add MockMvc tests for that branch and ensure Sentry calls can be isolated or disabled in tests.

## Acceptance Criteria

- [ ] Extend `backend/starter/src/test/java/cbs/nova/starter/DslExceptionHandlerTest.java` with a `DslException` branch test.
- [ ] Test asserts 422 Unprocessable Entity, `code`, `message`, `runId`, `exceptionId` in response.
- [ ] Sentry integration must not require network/api key in unit tests (mock `Sentry` or use no-op mode).
- [ ] Existing general/bad-request tests continue to pass.
- [ ] `./gradlew spotlessApply && ./gradlew :starter:test` passes.

## Files to Create / Modify

- `backend/starter/src/test/java/cbs/nova/starter/DslExceptionHandlerTest.java` — extend.

## Build / Test Commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test
```

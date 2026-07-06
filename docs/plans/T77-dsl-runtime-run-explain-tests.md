# T77 — DslRuntimeResource run/explain MockMvc tests

## Goal
Add MockMvc coverage for `POST /api/dsl/run/{name}` and `POST /api/dsl/explain/{name}` endpoints in `DslRuntimeResource`, including success, `DslException`, and generic failure paths. Locks the runtime REST contract and prevents regressions in the Runner UI.

## Tier
backend

## Files to create / modify
- Create: `backend/starter/src/test/java/cbs/nova/starter/DslRuntimeResourceRunExplainTest.java`
- Read only: `backend/starter/src/main/java/cbs/nova/starter/DslRuntimeResource.java`

## Acceptance criteria
- `run/{name}` returns 200 with result payload when `DslRuntime.run` succeeds.
- `run/{name}` returns 422 with structured `ErrorResponse` when `DslRuntime.run` returns a `DslException` failure.
- `run/{name}` returns 422 with runId/exceptionId when `DslRuntime.run` returns a generic exception failure.
- `explain/{name}` returns 200 with `ExplainReport` payload when `DslRuntime.explain` succeeds.
- New tests use existing MockMvc setup pattern and run with `./gradlew :starter:test`.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*DslRuntimeResourceRunExplainTest*'
```

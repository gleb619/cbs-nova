# T71 — DSL reload resource error-path tests

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

`DslReloadResource.reload()` has a Spring controller test file but the `NOT_CONFIGURED` and `NOT_FOUND` error branches are uncovered (codegraph confirms `reload` method has no covering tests). Add standalone MockMvc tests for those branches so the admin reload endpoint is fully specified.

## Acceptance Criteria

- [ ] Extend or replace `backend/starter/src/test/java/cbs/nova/starter/DslReloadResourceTest.java` with tests for the `reload()` method.
- [ ] Test `dsl.source-dir` blank/missing returns 409 with `NOT_CONFIGURED` code.
- [ ] Test configured directory that does not exist returns 409 with `NOT_FOUND` code.
- [ ] Keep existing general/bad request tests or move them to `DslExceptionHandlerTest` if duplicated.
- [ ] `./gradlew spotlessApply && ./gradlew :starter:test` passes.

## Files to Create / Modify

- `backend/starter/src/test/java/cbs/nova/starter/DslReloadResourceTest.java` — extend.

## Build / Test Commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test
```

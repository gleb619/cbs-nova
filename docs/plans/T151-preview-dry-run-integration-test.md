# T151 — Preview dry-run integration test

## Goal

Add an end-to-end integration test in `:starter` that executes a DSL flow in both `RUN` and `PREVIEW` modes. The flow performs real JDBC and Feign HTTP side effects when run; in preview it returns the same contract but different (mocked) content. The test verifies that preview captures external calls, builds a call-tree AST, and records dry-run logs.

## Tier

backend

## Files to create

- `backend/starter/src/integrationTest/java/cbs/nova/dsl/example/integration/PreviewDryRunIntegrationTest.java`
  The main test class:
  - `@SpringBootTest` with `StarterApplication.class`.
  - Test properties that configure an in-memory H2 datasource (`spring.datasource.url=jdbc:h2:mem:testdb`).
  - `@Import(PreviewDryRunTestConfig.class)` to bring in the test helper and Feign client.
  - Registers a DSL process inline that calls the test helper.
  - Calls `DslRuntime.run(...)` and `DslRuntime.preview(...)` and compares the results.

- `backend/starter/src/integrationTest/java/cbs/nova/dsl/example/integration/PreviewDryRunTestConfig.java`
  Test `@TestConfiguration` that:
  1. Defines a test `@Helper(name = "previewSideEffectsHelper")` bean using `JdbcTemplate` and a Feign client.
  2. Defines a Feign client interface and a factory method that builds the client with the `externalCallFeignInterceptor` bean.
  3. Starts a small JDK `HttpServer` stub that returns a fixed response.

## Files to modify

- `backend/starter/build.gradle`
  Ensure integration test classpath includes H2 (already present as `testImplementation 'com.h2database:h2'`) and OpenFeign (`testImplementation libs.openfeign.core` from T147).

## Acceptance criteria

- [ ] A DSL process is registered that, in `RUN` mode, writes a row via `JdbcTemplate` and calls an HTTP endpoint through Feign, then returns a real result.
- [ ] In `PREVIEW` mode the same process returns a `PreviewReport` whose output has the same type/contract but a visibly different value (e.g. real result contains actual counter, preview result contains a mock string).
- [ ] The preview report `externalCalls` list contains at least one `database` entry (SQL recorded) and at least one `http` entry (Feign request recorded).
- [ ] The preview report `astTree` has a root `CallNode` for the process and a child `CallNode` for the helper; helper children may include nested helper/transaction calls.
- [ ] The preview report `dryRunLogs` is non-empty and contains a log line emitted by the helper.
- [ ] Run mode does not produce a `PreviewReport`, does not populate dry-run logs, and performs real side effects verified by querying the H2 table.
- [ ] `./gradlew :starter:integrationTest --tests '*PreviewDryRunIntegrationTest*'` passes and `./gradlew spotlessApply` is clean.

## Build / test commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:integrationTest --tests '*PreviewDryRunIntegrationTest*'
```

## Implementation notes

- Keep the test self-contained: use H2 for the datasource and the JDK `com.sun.net.httpserver.HttpServer` for the HTTP stub. This avoids adding WireMock or Testcontainers for this test.
- The test helper should implement `Executable` and override `preview(...)` to return the mock result without side effects, while `execute(...)` performs the JDBC/Feign calls. This directly exercises the `Executable.preview` contract from T65.
- Register the DSL process and helper at test setup time through `GlobalManager.globalManager().registerProcess(...)` and the helper bean through Spring (the existing `SpringBeanHelperInstanceResolver` will resolve it).
- Use the `DslRuntime` bean from the Spring context for both `run` and `preview` invocations; alternatively exercise the REST endpoint with `TestRestTemplate` or `MockMvc`. Using the service bean keeps the test focused on the artifacts.

## Commit message

```
feat(T151): add preview dry-run integration test for DB/Feign capture, AST, and logs
```

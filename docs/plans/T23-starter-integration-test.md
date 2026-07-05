# T23 — Starter Integration Test

## Goal

Add a `@SpringBootTest` integration test that loads a real DSL definition from a temp directory and
exercises Preview and Explain endpoints via `DslRuntimeResource`. Validates the full path:
configure source-dir → `DefinitionLoader.load()` → `DevDslRuntime.dispatch()` → HTTP response.

## Acceptance Criteria

- `DslStarterIntegrationTest` in `cbs.nova.starter` package
- Uses `@SpringBootTest` with a real application context (no mocks for core beans)
- Writes a valid compact-source DSL file to a `@TempDir` before context starts
- Sets `dsl.source-dir` property to the temp dir path via `@TestPropertySource` or `@DynamicPropertySource`
- Calls `POST /api/dsl/preview/{name}` and asserts 200 response
- Calls `POST /api/dsl/explain/{name}` and asserts 200 response with non-null `mermaidDiagram`
- `GlobalManager.resetForTests()` called in `@AfterEach` to clean up

## DSL source content to write

```java
import cbs.nova.dsl.*;
import java.util.List;

void main() {}

List<DslObject> define() {
  return Dsl.process("IntegrationTest")
      .execute(ctx -> Result.success("ok"))
      .buildList();
}
```

## Files to Create

- **Create**: `backend/starter/src/test/java/cbs/nova/starter/DslStarterIntegrationTest.java`

## Build & Test

From `backend/`:
```
./gradlew spotlessApply
./gradlew :starter:test --tests cbs.nova.starter.DslStarterIntegrationTest
```

## Constraints

- Java 25, 2-space indent, Spotless must pass
- Depends on T20 (`DslAutoConfiguration`) being done
- Use `MockMvcBuilders.standaloneSetup(dslRuntimeResource)` if `@WebMvcTest` is unavailable
- Call `GlobalManager.resetForTests()` in `@AfterEach`
- Commit: `feat(T23): add starter integration test for full DSL pipeline`

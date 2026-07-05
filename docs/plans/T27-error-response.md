# T27 — REST Error Response Standardization

## Goal

Replace bare string error bodies in `DslRuntimeResource` with a structured JSON `ErrorResponse` record.
Clients currently get a plain string on 422; with this change they get `{"code":"...","message":"...","entityName":"..."}`.

## Acceptance Criteria

- New record `ErrorResponse` in `cbs.nova.starter` (or `cbs.nova.dsl` if it belongs to the API):
  ```java
  public record ErrorResponse(String code, String message, String entityName) {}
  ```
- `DslRuntimeResource.preview()` and `run()` return `ResponseEntity<ErrorResponse>` on failure:
  - `code` = `"EXECUTION_FAILED"`
  - `message` = `result.cause().getMessage()`
  - `entityName` = the `{name}` path variable
- `DslRuntimeResource.explain()` on caught exception returns 500 with `code = "EXPLAIN_FAILED"`
- `DslRuntimeResourceTest` updated: failure assertions check JSON fields `$.code`, `$.message`, `$.entityName`
  instead of raw string body

## Files to Modify

- **Modify**: `backend/starter/src/main/java/cbs/nova/starter/DslRuntimeResource.java`
- **Modify**: `backend/starter/src/test/java/cbs/nova/starter/DslRuntimeResourceTest.java`

## Implementation Notes

`preview()` method change:
```java
return result.isSuccess()
    ? ResponseEntity.ok(result.value())
    : ResponseEntity.unprocessableEntity()
        .body(new ErrorResponse("EXECUTION_FAILED", result.cause().getMessage(), name));
```

Return type changes from `ResponseEntity<?>` to `ResponseEntity<Object>`.

Test assertion change (was `content().string(...)`):
```java
.andExpect(jsonPath("$.code").value("EXECUTION_FAILED"))
.andExpect(jsonPath("$.message").value("boom"))
.andExpect(jsonPath("$.entityName").value("Fail"))
```

## Build & Test

From `backend/`:
```
./gradlew spotlessApply
./gradlew :starter:build :starter:test
```

## Constraints

- Java 25, 2-space indent, Spotless must pass
- Only modify `starter/` module
- Keep `DslStarterIntegrationTest` green — update its 422 assertion too if needed
- Commit: `feat(T27): add structured ErrorResponse JSON on DSL execution failure`

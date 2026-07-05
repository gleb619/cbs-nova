# T31 — Entity Detail Endpoints

## Goal

Extend `DslIntrospectionResource` with detail endpoints returning metadata for a specific
registered process or transaction: version, taskQueue, inputType, outputType, hasCompensation.

## Acceptance Criteria

- `GET /api/dsl/processes/{name}` returns `ProcessDetail` JSON on 200, 404 if not found
- `GET /api/dsl/transactions/{name}` returns `TransactionDetail` JSON on 200, 404 if not found
- `ProcessDetail` record: `{name, version, taskQueue, inputType, outputType, hasCompensation}`
- `TransactionDetail` record: `{name, version, taskQueue, inputType, outputType, hasCompensation, startToCloseTimeoutMs}`
- Uses `GlobalManager.findProcess()` and `findTransaction()` (already exist from T24)
- Types exposed as simple class name strings (e.g. `"String"`, `"Object"`, `null`)
- Unit test: `DslIntrospectionResourceTest` — add cases for detail endpoints (200 and 404)

## Files to Modify

- **Modify**: `backend/starter/src/main/java/cbs/nova/starter/DslIntrospectionResource.java`
  — add `@GetMapping("/processes/{name}")` and `@GetMapping("/transactions/{name}")` handlers
  — add `ProcessDetail` and `TransactionDetail` records
- **Modify**: `backend/starter/src/test/java/cbs/nova/starter/DslIntrospectionResourceTest.java`
  — add test cases for the new endpoints

## Implementation Notes

```java
@GetMapping("/processes/{name}")
public ResponseEntity<?> processDetail(@PathVariable String name) {
  return GlobalManager.getInstance()
      .findProcess(name)
      .<ResponseEntity<?>>map(p -> ResponseEntity.ok(new ProcessDetail(
          p.name(), p.version(), p.taskQueue(),
          typeName(p.inputType()), typeName(p.outputType()),
          p.compensationLogic() != null)))
      .orElse(ResponseEntity.notFound().build());
}

private static String typeName(Class<?> type) {
  return type == null ? null : type.getSimpleName();
}

public record ProcessDetail(String name, String version, String taskQueue,
    String inputType, String outputType, boolean hasCompensation) {}
```

`TransactionDetail` similar but also includes `startToCloseTimeoutMs` from `TransactionDslObject.startToCloseTimeout().toMillis()`.

Read `TransactionDslObject.java` first to confirm field name.

## Build & Test

From `backend/`:
```
./gradlew spotlessApply
./gradlew :starter:build :starter:test
```

## Constraints
- Java 25, 2-space indent, Spotless must pass
- Only modify `starter/` module
- 404 (not 422) for unknown entity name
- Commit: `feat(T31): add entity detail endpoints for process and transaction metadata`

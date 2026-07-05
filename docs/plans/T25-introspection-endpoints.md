# T25 — DSL Introspection Endpoints

## Goal

Add read-only HTTP endpoints that list names of registered DSL entities, enabling UI tooling and
health checks to discover what's loaded at runtime.

## Acceptance Criteria

- New controller `DslIntrospectionResource` in `cbs.nova.starter` with 3 endpoints:
  - `GET /api/dsl/processes` → `{"names": ["LoanDisbursement", ...]}`
  - `GET /api/dsl/transactions` → `{"names": [...]}`
  - `GET /api/dsl/helpers` → `{"names": [...]}`
- Returns HTTP 200 with JSON; empty list when nothing is registered
- `GlobalManager` must expose name-listing methods (or delegate to managers)
- Unit test: `DslIntrospectionResourceTest` using `MockMvcBuilders.standaloneSetup()`

## Files to Create / Modify

- **Create**: `backend/starter/src/main/java/cbs/nova/starter/DslIntrospectionResource.java`
- **Create**: `backend/starter/src/test/java/cbs/nova/starter/DslIntrospectionResourceTest.java`
- **Modify**: `backend/dsl/src/main/java/cbs/nova/dsl/GlobalManager.java` — add `processNames()`,
  `transactionNames()`, `helperNames()` returning `List<String>`
- **Modify**: `backend/dsl/src/main/java/cbs/nova/dsl/ProcessManager.java` — add `names()`
- **Modify**: `backend/dsl/src/main/java/cbs/nova/dsl/TransactionManager.java` — add `names()`
- **Modify**: `backend/dsl/src/main/java/cbs/nova/dsl/HelperManager.java` — add `names()`
- **Modify**: `backend/starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — add `cbs.nova.starter.DslIntrospectionResource` (or register as @Bean in TemporalConfiguration)

## Implementation Notes

`GlobalManager` additions:
```java
public List<String> processNames() { return processManager.names(); }
public List<String> transactionNames() { return transactionManager.names(); }
public List<String> helperNames() { return helperManager.names(); }
```

`DslIntrospectionResource`:
```java
@RestController
@RequestMapping("/api/dsl")
public class DslIntrospectionResource {
  @GetMapping("/processes")
  public ResponseEntity<NamesResponse> processes() {
    return ResponseEntity.ok(new NamesResponse(GlobalManager.getInstance().processNames()));
  }
  // similar for transactions, helpers
  public record NamesResponse(List<String> names) {}
}
```

## Build & Test

From `backend/`:
```
./gradlew spotlessApply
./gradlew :dsl:build :dsl:test :starter:build :starter:test
```

## Constraints

- Java 25, 2-space indent, Spotless must pass
- Only modify `dsl/` and `starter/` modules
- Commit: `feat(T25): add DSL introspection endpoints listing registered entities`

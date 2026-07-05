# T20 — DSL Auto-Configuration

## Goal

Add a Spring Boot auto-configuration bean to `starter` that reads a `dsl.source-dir` property and calls
`DefinitionLoader.load(sourceDir, GlobalManager.getInstance())` on startup. This enables Preview and Explain
modes to work without any manual setup.

## Acceptance Criteria

- `DslAutoConfiguration` bean exists in `cbs.nova.starter`
- Reads property `dsl.source-dir` (defaults to empty string / disabled when blank)
- On startup (`@PostConstruct`), calls `DefinitionLoader.load()` with the configured path
- Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Unit test: `DslAutoConfigurationTest` verifies bean is created and `DefinitionLoader` is invoked with configured dir

## Files to Create / Modify

- **Create**: `backend/starter/src/main/java/cbs/nova/starter/DslAutoConfiguration.java`
- **Create**: `backend/starter/src/test/java/cbs/nova/starter/DslAutoConfigurationTest.java`
- **Modify**: `backend/starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — add `cbs.nova.starter.DslAutoConfiguration`

## Implementation Notes

```java
@AutoConfiguration
public class DslAutoConfiguration {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  @PostConstruct
  void load() {
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) return;
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException("dsl.source-dir does not exist: " + dir);
    }
    DefinitionLoader.load(dir, GlobalManager.getInstance());
  }
}
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
- Do not modify `dsl/` or `dsl-api/`
- Commit: `feat(T20): add DSL auto-configuration for source-dir property`

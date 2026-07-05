# T26 — Spring PropertyResolver Wiring

## Goal

`PropertyResolver` exists in `dsl` module but is never used at runtime. Wire it to Spring `Environment`
so DSL task-queue names and other string fields with `${key}` placeholders resolve from
`application.properties`.

## Acceptance Criteria

- `PropertyResolverConfiguration` Spring auto-configuration bean in `cbs.nova.starter`
- Reads all resolvable properties from Spring `Environment` and creates a `PropertyResolver` bean
- `DslAutoConfiguration` accepts optional `PropertyResolver` parameter; if present, calls
  `DefinitionLoader.load(dir, gm)` after resolving placeholder strings in loaded object names
- Unit test: `PropertyResolverConfigurationTest` verifying bean is created and resolves a test property

## Files to Create / Modify

- **Create**: `backend/starter/src/main/java/cbs/nova/starter/PropertyResolverConfiguration.java`
- **Create**: `backend/starter/src/test/java/cbs/nova/starter/PropertyResolverConfigurationTest.java`
- **Modify**: `backend/starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — add `cbs.nova.starter.PropertyResolverConfiguration`

## Implementation Notes

```java
@AutoConfiguration
public class PropertyResolverConfiguration {

  @Bean
  @ConditionalOnMissingBean
  PropertyResolver dslPropertyResolver(Environment environment) {
    // Collect all resolvable properties from Spring Environment into a Map
    // Spring Environment doesn't expose a full property map directly — use
    // ConfigurableEnvironment and iterate PropertySources
    var props = new HashMap<String, String>();
    if (environment instanceof ConfigurableEnvironment ce) {
      for (var source : ce.getPropertySources()) {
        if (source instanceof MapPropertySource mps) {
          mps.getPropertyNames().forEach(k -> {
            var v = environment.getProperty(k);
            if (v != null) props.put(k, v);
          });
        }
      }
    }
    return new PropertyResolver(props, false); // failOnMissing=false for resilience
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
- `failOnMissing = false` — unresolved placeholders pass through unchanged
- Commit: `feat(T26): add Spring PropertyResolver auto-configuration wired to Environment`

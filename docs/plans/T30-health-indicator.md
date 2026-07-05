# T30 — DSL HealthIndicator

## Goal

Add a Spring Boot `HealthIndicator` that reports the count and names of loaded DSL entities.
Surfaces via `/actuator/health` when spring-boot-actuator is on the classpath.

## Acceptance Criteria

- `DslHealthIndicator` implements `HealthIndicator` in `cbs.nova.starter`
- Returns `UP` with details `{processes: N, transactions: N, helpers: N}` when ≥1 entity loaded
- Returns `UP` with details when 0 entities loaded (not DOWN — missing DSL is not fatal)
- Registered as `@Bean` in `TemporalConfiguration` or as auto-configuration
- `@ConditionalOnClass(HealthIndicator.class)` so it's a no-op without actuator
- Unit test: `DslHealthIndicatorTest` verifying details map keys exist

## Files to Create / Modify

- **Create**: `backend/starter/src/main/java/cbs/nova/starter/DslHealthIndicator.java`
- **Create**: `backend/starter/src/test/java/cbs/nova/starter/DslHealthIndicatorTest.java`
- **Modify**: `backend/starter/src/main/java/cbs/nova/starter/TemporalConfiguration.java`
  — add `@Bean @ConditionalOnClass(HealthIndicator.class) DslHealthIndicator dslHealthIndicator()`

## Implementation Notes

```java
@Component
@ConditionalOnClass(HealthIndicator.class)
public class DslHealthIndicator implements HealthIndicator {
  @Override
  public Health health() {
    GlobalManager gm = GlobalManager.getInstance();
    return Health.up()
        .withDetail("processes", gm.processNames().size())
        .withDetail("transactions", gm.transactionNames().size())
        .withDetail("helpers", gm.helperNames().size())
        .build();
  }
}
```

Check if `spring-boot-starter-actuator` is in `backend/starter/build.gradle`.
If not, add: `implementation 'org.springframework.boot:spring-boot-starter-actuator'`
(or use `compileOnly` if actuator is optional).

## Build & Test

From `backend/`:
```
./gradlew spotlessApply
./gradlew :starter:build :starter:test
```

## Constraints
- Java 25, 2-space indent, Spotless must pass
- `@ConditionalOnClass(HealthIndicator.class)` — actuator is optional dep
- Always returns UP — empty registry is not an error condition
- Only modify `starter/` module
- Commit: `feat(T30): add DslHealthIndicator for Spring Boot actuator`

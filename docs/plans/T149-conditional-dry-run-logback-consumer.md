# T149 — Conditional dry-run Logback consumer

## Goal

Capture log events emitted during DSL preview/explain and return them in the report, while leaving normal run-mode logs unchanged. Implement this as a Logback appender/consumer in `:starter` that is active only when a per-thread dry-run context is present.

## Tier

backend

## Files to create

- `backend/starter/src/main/java/cbs/nova/starter/logging/DryRunLoggingContext.java`
  Static helper using `ThreadLocal<String>` to hold the active dry-run `runId`. Methods:
  - `enterDryRun(String runId)`
  - `leaveDryRun()`
  - `currentRunId()`

- `backend/starter/src/main/java/cbs/nova/starter/logging/DryRunLogbackAppender.java`
  Extends `ch.qos.logback.core.AppenderBase<ILoggingEvent>` (Logback is transitively provided by Spring Boot). It stores events in a runId-keyed circular buffer only when `DryRunLoggingContext.currentRunId()` is non-null. Exposes:
  - `List<Map<String,Object>> drain(String runId)` — returns and clears the captured events for that runId.

- `backend/starter/src/main/resources/logback-spring.xml`
  Registers the appender on the root logger with a reasonable max buffer size (e.g. 1000 events). The appender should not duplicate console output; it only records.

## Files to create (tests)

- `backend/starter/src/test/java/cbs/nova/starter/logging/DryRunLogbackAppenderTest.java`
  Tests:
  1. A log statement emitted inside `DryRunLoggingContext.enterDryRun(runId)` is captured.
  2. A log statement emitted outside a dry-run context is not captured.
  3. `drain(runId)` returns the captured events and clears the buffer.
  4. Events from different runIds are isolated.

## Acceptance criteria

- [ ] The appender is automatically registered via `logback-spring.xml` when the starter is on the classpath.
- [ ] It only records log events when `DryRunLoggingContext` has an active runId; otherwise it is a no-op.
- [ ] Each captured event exposes `timestamp`, `level`, `logger`, and `message` in a stable map shape.
- [ ] `drain(runId)` returns the events for that runId and removes them from the buffer to prevent unbounded growth.
- [ ] Run-mode logs are unaffected.
- [ ] New unit test passes and `./gradlew spotlessApply` is clean.

## Build / test commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*DryRunLogbackAppenderTest*'
```

## Implementation notes

- Logback classes are available at runtime through Spring Boot; declare the dependency as `compileOnly` if the build file does not already provide it transitively.
- Use a `ConcurrentHashMap<String, List<Map<String,Object>>>` with `Collections.synchronizedList` or a bounded `ArrayDeque` per runId. Cap the buffer to avoid memory issues.
- `DevDslRuntime` (T150) will call `DryRunLoggingContext.enterDryRun(runId)` before dispatch and `leaveDryRun()` in a `finally` block, then read the drained logs for the report.

## Commit message

```
feat(T149): add conditional dry-run Logback consumer for preview/explain logs
```

# T21 — Temporal Worker Bootstrap

## Goal

Add `DslWorkerConfiguration` to `starter` that creates a Temporal Worker and registers generated
`*ProcessDefinition` and `*TransactionDefinition` implementations found on the classpath. This enables
actual Run mode (dispatching to a real Temporal cluster).

## Acceptance Criteria

- `DslWorkerConfiguration` Spring bean creates a `Worker` on task queue `dsl.task-queue` (default: `"dsl-task-queue"`)
- Scans classpath for classes that implement `io.temporal.workflow.Workflow` (process) or are annotated with
  `@ActivityImplementation` (transaction) in the `cbs.nova.dsl.generated` package
- Registers found impls with `worker.registerWorkflowImplementationTypes()` and `worker.registerActivitiesImplementations()`
- Worker is conditional on `dsl.worker.enabled=true` (default `false` so tests/preview mode don't need Temporal)
- Test: `DslWorkerConfigurationTest` verifies worker bean is NOT created when `dsl.worker.enabled=false`

## Files to Create / Modify

- **Create**: `backend/starter/src/main/java/cbs/nova/starter/DslWorkerConfiguration.java`
- **Create**: `backend/starter/src/test/java/cbs/nova/starter/DslWorkerConfigurationTest.java`
- **Modify**: `backend/starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — add `cbs.nova.starter.DslWorkerConfiguration`

## Implementation Notes

```java
@AutoConfiguration
@ConditionalOnProperty(name = "dsl.worker.enabled", havingValue = "true")
public class DslWorkerConfiguration {

  @Value("${dsl.task-queue:dsl-task-queue}")
  private String taskQueue;

  @Bean
  Worker dslWorker(WorkflowClient workflowClient) {
    var factory = WorkerFactory.newInstance(workflowClient);
    var worker = factory.newWorker(taskQueue);
    // Classpath scan for generated impls omitted in stub — agent fills in
    factory.start();
    return worker;
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
- Worker must default to disabled so existing tests don't try to connect to Temporal
- Commit: `feat(T21): add Temporal worker bootstrap with classpath scanning`

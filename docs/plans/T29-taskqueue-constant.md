# T29 — TASK_QUEUE Constant in Generated Classes

## Goal

`ProcessCodeGenerator` and `TransactionCodeGenerator` generate implementations that don't emit the
`taskQueue` from the descriptor. Add `private static final String TASK_QUEUE` constant to generated
`*ProcessDefinition` and `*TransactionDefinition` classes.

## Acceptance Criteria

- Generated `*ProcessDefinition` contains: `private static final String TASK_QUEUE = "loandisbursement-queue";`
- Generated `*TransactionDefinition` contains: `private static final String TASK_QUEUE = "kycchecktransaction-queue";` (or whatever the descriptor value is)
- Both generators pass the `taskQueue` string into `generateImpl()`
- `ProcessCodeGeneratorTest` gains assertion: `impl.source().contains("TASK_QUEUE")`
- `TransactionCodeGeneratorTest` gains same assertion
- Default task queue value comes from `ProcessBuilder`: `name + "-queue"` (already wired in ProcessBuilder)

## Files to Modify

- **Modify**: `backend/dsl-codegen/src/main/java/cbs/nova/dsl/codegen/ProcessCodeGenerator.java`
  - Pass `descriptor.taskQueue()` to `generateImpl()`
  - In `generateImpl()`, emit `private static final String TASK_QUEUE = "<value>";\n`
- **Modify**: `backend/dsl-codegen/src/main/java/cbs/nova/dsl/codegen/TransactionCodeGenerator.java`
  - Same pattern
- **Modify**: `backend/dsl-codegen/src/test/java/cbs/nova/dsl/codegen/ProcessCodeGeneratorTest.java`
  - Add assertion on TASK_QUEUE
- **Modify**: `backend/dsl-codegen/src/test/java/cbs/nova/dsl/codegen/TransactionCodeGeneratorTest.java`
  - Add assertion on TASK_QUEUE

## Implementation Notes

In `ProcessCodeGenerator.generate()`, change:
```java
new GeneratedSource(pkg, implName, generateImpl(pkg, name, interfaceName, implName, descriptor.version(), descriptor.hasCompensation()))
```
to pass `descriptor.taskQueue()` as additional argument.

In `generateImpl()`, after VERSION field add:
```java
+ "  private static final String TASK_QUEUE = \"" + taskQueue + "\";\n\n"
```

## Build & Test

From `backend/`:
```
./gradlew spotlessApply
./gradlew :dsl-codegen:build :dsl-codegen:test
```

## Constraints
- Java 25, 2-space indent, Spotless must pass
- Only modify `dsl-codegen/` module
- Commit: `feat(T29): emit TASK_QUEUE constant in generated ProcessDefinition and TransactionDefinition`

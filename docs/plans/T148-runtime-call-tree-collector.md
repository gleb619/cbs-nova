# T148 — Runtime call-tree collector for preview/explain

## Goal

Extend the DSL runtime so that preview and explain modes can return a nested call tree (an AST of runtime invocations) in addition to the flat execution trace. The tree captures the hierarchy of process → transaction/helper/function calls and is included in `PreviewReport` / `ExplainReport` by T150.

## Tier

backend

## Files to create

- `backend/dsl-api/src/main/java/cbs/nova/dsl/CallKind.java`
  Enum values: `PROCESS`, `TRANSACTION`, `HELPER`, `FUNCTION`.

- `backend/dsl-api/src/main/java/cbs/nova/dsl/CallNode.java`
  Immutable record representing one invocation:
  ```java
  public record CallNode(
      @NonNull String name,
      @NonNull CallKind kind,
      @Nullable Object input,
      @Nullable Object output,
      boolean success,
      @NonNull List<CallNode> children,
      @NonNull List<Map<String, Object>> externalCalls) {}
  ```

- `backend/dsl/src/main/java/cbs/nova/dsl/ExecutionTreeCollector.java`
  RunId-scoped collector with a stack per `runId`:
  - `startRun(String runId, CallNode root)` / `finishRun(String runId)`
  - `enter(String runId, String name, CallKind kind, Object input)` → returns current `CallNode`
  - `exit(String runId, Object output, boolean success, List<Map<String,Object>> externalCalls)` → pops stack and attaches node to parent.
  - `tree(String runId)` → returns the root `CallNode`.

## Files to modify

- `backend/dsl-api/src/main/java/cbs/nova/dsl/ExecutionListener.java`
  Add optional start/end default methods so runners can notify without breaking existing listeners:
  ```java
  default void onProcessStart(String runId, String name, Object input) {}
  default void onProcessEnd(String runId, String name, Object output, boolean success) {}
  default void onTransactionStart(String runId, String name, Object input) {}
  default void onTransactionEnd(String runId, String name, Object output, boolean success) {}
  default void onHelperStart(String runId, String name, Object input) {}
  default void onHelperEnd(String runId, String name, Object output, boolean success) {}
  ```

- `backend/dsl/src/main/java/cbs/nova/dsl/runner/DefaultProcessRunner.java`
  Wrap process execution in `enter/exit` around `runDirectly`/`launchWithTemporal`. Skip tree building when launched by Temporal (run mode) because the report is not produced there.

- `backend/dsl/src/main/java/cbs/nova/dsl/runner/DefaultTransactionRunner.java`
  Wrap transaction execution in `enter/exit`.

- `backend/dsl/src/main/java/cbs/nova/dsl/runner/DefaultHelperRunner.java`
  Wrap helper and function execution in `enter/exit`.

## Files to create (tests)

- `backend/dsl/src/test/java/cbs/nova/dsl/ExecutionTreeCollectorTest.java`
  Tests:
  1. Single-level tree (process only).
  2. Nested tree: process → helper → transaction.
  3. RunId isolation: two concurrent runs produce independent trees.
  4. External calls attached to the correct node.

## Acceptance criteria

- [ ] `CallNode` and `CallKind` are public API in `dsl-api` with no external dependencies.
- [ ] `ExecutionTreeCollector` is runId-scoped and safe for synchronous preview/explain use (concurrent runIds do not corrupt each other's stacks).
- [ ] Runners notify start/end for process, transaction, helper, and function invocations.
- [ ] Existing `ExecutionListener` implementations compile without modification thanks to default methods.
- [ ] `DevDslRuntime` (in T150) can read the finished tree and place it into the report.
- [ ] New unit tests pass and `./gradlew spotlessApply` is clean.

## Build / test commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test --tests '*ExecutionTreeCollectorTest*'
```

## Implementation notes

- Keep the collector stack-based: each `enter` pushes a node, each `exit` pops and attaches to the new top. If no node is active, events are ignored.
- External calls for a node can be collected from `ExternalCallTracker.getActiveTracking()` at exit time, or a dedicated listener can append them. For simplicity, snapshot the active thread-local calls at exit and move them into the node.
- Do not attach the tree to `Context` directly; pass it through `DevDslRuntime` in T150.

## Commit message

```
feat(T148): runtime call-tree collector for preview/explain AST
```

# T150 — Enrich preview/explain reports with call-tree and dry-run logs

## Goal

Wire the call-tree collector (T148) and the dry-run log consumer (T149) into `DevDslRuntime` so that `PreviewReport` and `ExplainReport` include both the nested AST of calls and the logs captured during dry-run execution.

## Tier

backend

## Files to modify

- `backend/dsl-api/src/main/java/cbs/nova/dsl/PreviewReport.java`
  Add fields:
  ```java
  @Nullable CallNode astTree,
  @NonNull List<Map<String, Object>> dryRunLogs
  ```
  Keep `dryRunLogs` non-null default empty list.

- `backend/dsl-api/src/main/java/cbs/nova/dsl/ExplainReport.java`
  Add the same two fields.

- `backend/starter/src/main/java/cbs/nova/starter/DevDslRuntime.java`
  1. Start `ExecutionTraceCollector` and `ExecutionTreeCollector` around dispatch for preview/explain.
  2. Call `DryRunLoggingContext.enterDryRun(runId)` before dispatch and `leaveDryRun()` in `finally`.
  3. After dispatch, read the finished tree and drained logs and include them in the constructed `PreviewReport` / `ExplainReport`.
  4. For `run()` mode, do not start the tree collector or log context.

- Tests that construct report records directly:
  - `backend/starter/src/test/java/cbs/nova/starter/DevDslRuntimeTest.java`
  - `backend/starter/src/test/java/cbs/nova/starter/DslRuntimeResourceTest.java`
  - Any `dsl-api` record tests (`PreviewReport`/`ExplainReport` constructors in T124)
  Update constructors/assertions to account for the new fields.

## Acceptance criteria

- [ ] `PreviewReport` and `ExplainReport` records carry `astTree` and `dryRunLogs` fields.
- [ ] `DevDslRuntime.preview` returns a report whose `astTree` root matches the executed process/transaction/helper name and contains children for nested calls.
- [ ] `DevDslRuntime.explain` returns a report with the same tree and log fields populated.
- [ ] `DevDslRuntime.run` is unchanged and does not capture dry-run logs or AST.
- [ ] Reports remain JSON-serializable through the existing Jackson/REST layer.
- [ ] `./gradlew :dsl-api:test :dsl:test :starter:test` passes after updating affected tests.

## Build / test commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl-api:test :dsl:test :starter:test
```

## Implementation notes

- Make the new fields nullable/default-empty so that existing report construction in tests can be updated mechanically.
- `ExecutionTreeCollector` should be a bean or a simple instance created per call in `DevDslRuntime`. Because it is runId-scoped, creating a fresh instance per preview/explain call is the safest approach.
- Keep the existing `executionTrace` flat list unchanged; `astTree` is an additional view.
- If the executed entity is a top-level helper, the tree root is a `CallNode` of kind `HELPER`.

## Commit message

```
feat(T150): include call-tree and dry-run logs in preview/explain reports
```

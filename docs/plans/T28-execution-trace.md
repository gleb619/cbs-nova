# T28 — Execution Trace in ExplainReport

## Goal

`ExplainReport.executionTrace()` always returns `List.of()`. Populate it with meaningful step entries
during Explain mode dispatch in `DevDslRuntime.explain()`.

## Acceptance Criteria

- `ExplainReport.executionTrace()` contains at least 3 entries on success:
  - `"started: <name>"`
  - `"mode: EXPLAIN"`
  - `"result: success"` (or `"result: <value>"` if value is a string)
- On failure: `"started: <name>"`, `"mode: EXPLAIN"`, `"result: failure: <error message>"`
- `DevDslRuntimeTest.explainReturnsReport()` updated to assert trace is non-empty
- New test: `explainTraceContainsSteps()` asserting specific entries

## Files to Modify

- **Modify**: `backend/starter/src/main/java/cbs/nova/starter/DevDslRuntime.java`
- **Modify**: `backend/starter/src/test/java/cbs/nova/starter/DevDslRuntimeTest.java`

## Implementation Notes

In `DevDslRuntime.explain()`, replace `List.of()` with:
```java
var trace = new ArrayList<String>();
trace.add("started: " + name);
trace.add("mode: EXPLAIN");
if (result.isSuccess()) {
  Object val = result.value();
  trace.add("result: " + (val != null ? val.toString() : "null"));
} else {
  trace.add("result: failure: " + result.cause().getMessage());
}
return new ExplainReport(name, description, mermaid, List.copyOf(trace));
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
- Commit: `feat(T28): populate executionTrace in ExplainReport`

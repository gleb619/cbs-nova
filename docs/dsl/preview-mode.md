# Preview Mode (Dry-Run)

Preview mode is a **safe, local, Temporal-free execution path** for DSL definitions. It runs the same
Processes, Transactions, Helpers, and Functions as production mode, but without starting workers, connecting to a
Temporal cluster, or committing real side effects. The result is a **report** that a coding agent, a developer, or a
business reviewer can read to understand what the flow would do.

## Why Preview exists

The engine is built so that the same DSL definition can be executed in three modes: `run`, `preview`, and `explain`.
Preview sits in the middle: it is fast like Explain, but it executes real business logic like Run. It was added
primarily for **coding agents and automated review tools** that need to:

1. **Verify work without deploying.** An agent can change a DSL flow, preview it, and confirm the output shape and
   execution path without a Temporal cluster or real downstream services.
2. **Produce a report that is easy to explain.** Preview returns a structured `PreviewReport` containing the final
   output, a flat execution trace, a nested call-tree AST, captured external calls, and dry-run logs. This gives an
   agent concrete evidence of what happened and why.
3. **Stay safe around side effects.** A well-behaved Helper or Transaction can override `preview(...)` to return mock
   data or skip destructive operations. Preview never triggers Temporal activities, so databases, queues, and HTTP
   services are not touched unless the DSL explicitly chooses to.
4. **Speed up iteration.** Preview runs synchronously inside the JVM, so a flow completes in milliseconds instead of
   waiting for network round-trips and workflow scheduling.

## What Preview is not

- It is **not a full production run.** Durability, retries, versioning, and Saga compensation are exercised differently
  because there is no Temporal workflow execution.
- It is **not a static analyzer.** Preview executes code, so it still runs helper logic and can fail on real exceptions.
- It is **not a substitute for integration tests.** It is a fast feedback tool, but final validation still belongs in
  run-mode integration tests against a real Temporal environment.

## How Preview works

1. **No Temporal cluster is needed.** The DSL definition is executed directly through `GlobalManager` and the runner
   layer, bypassing generated workflow/activity classes.
2. **The same `Context` contract is used.** Input and output payloads, metadata, and typed results are identical to run
   mode, so generated code and helpers do not need special-casing.
3. **Transactions run locally.** When a Process calls `runTransaction(...)`, the manager resolves the DSL Transaction
   definition and runs it through `TransactionRunner` instead of dispatching to a Temporal activity stub.
4. **Helpers and Functions run as-is.** `runHelper(...)` calls go through `HelperRunner`. Authors can provide a
   `preview(...)` implementation that returns safe mock data while keeping `execute(...)` unchanged for production.
5. **Compensation can be simulated.** If a step throws, the same reverse-order compensation logic used in run mode can
   be triggered to verify rollback paths.
6. **A report is returned.** The runtime collects execution trace entries, external calls, a nested call-tree AST, and
   dry-run logs, then bundles them into a `PreviewReport`.

## The PreviewReport

```java
public record PreviewReport(
    String name,
    ExecutionMode mode,
    boolean success,
    Object output,
    List<String> executionTrace,
    List<Map<String, Object>> externalCalls,
    Map<String, Integer> callCounts,
    CallNode astTree,
    List<Map<String, Object>> dryRunLogs,
    PreviewMetricsSnapshot metrics,
    List<PreviewErrorDetail> errors
) {}
```

| Field | Purpose |
|-------|---------|
| `name` | The DSL entity that was previewed. |
| `mode` | The execution mode (`PREVIEW`, `RUN`, `EXPLAIN`, etc.). |
| `success` | Whether the preview completed without a runtime failure. |
| `output` | The final payload returned by the flow. It has the same type/contract as run mode, but may contain mock values. |
| `executionTrace` | Flat, human-readable list of steps in order (e.g., `started: LoanProcess`, `called helper: riskAssessment`). |
| `externalCalls` | List of captured external interactions such as JDBC, HTTP, MQ, and Feign calls. |
| `callCounts` | Aggregated counts by call type (e.g., `database=2`, `http=1`). |
| `astTree` | Nested call-tree AST showing the hierarchy of process → helper/transaction/function calls. |
| `dryRunLogs` | Log events captured during the preview, useful for debugging and explanation. |
| `metrics` | Optional performance/diagnostics snapshot captured during the run. |
| `errors` | Optional list of non-fatal preview errors or diagnostics. |

## When to use Preview

- **Local development:** quickly validate a new or changed DSL flow before committing.
- **CI validation:** run preview against a suite of flows to catch syntax, type, and semantic errors early.
- **Agent-driven changes:** an agent can preview its own edits and include the report in its final response, making the
  work transparent and reviewable.
- **Documentation:** preview reports can be committed or rendered as living documentation, especially when combined with
  Explain mode's diagrams.

## Relationship to other modes

- **Run mode** executes the generated Temporal workflows and activities against a real cluster. Use it for production
  and final integration testing.
- **Preview mode** executes the same DSL logic locally and returns a report. Use it for fast validation and
  explainability.
- **Explain mode** is Preview plus a natural-language description and diagrams. Use it when the goal is to communicate
  the flow to humans rather than verify behavior.

## Related docs

- [Runtime Engine](runtime.md) — registry, runner, manager, and mode-agnostic REST surface.
- [DSL Constructs & Execution Contract](constructs.md) — the `Executable` interface and `preview(...)` method contract.
- [Dry-run/Preview and Explain Modes](../ideas/dry-run-preview-explain.ignore.md) — detailed endpoints, output formats, and
  diagram library integrations.
- `docs/plans/T146-*` through `T151-*` — implementation tasks for external call capture, call-tree collection, dry-run
  logs, and the integration test.

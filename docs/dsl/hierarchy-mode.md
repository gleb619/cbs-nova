# Hierarchy Mode

Hierarchy mode is a **safe, local, structured call-graph report** for a DSL definition. It walks the
same Process → Transaction → Helper/Function graph that Preview walks, but instead of returning the
final business output it returns a graph-shaped report of the execution itself: the call tree,
execution trace, captured external calls, call counts, dry-run logs, metrics, errors, and a
per-node diagram. Hierarchy is the shared substrate that Explain and Preview build on.

## Why Hierarchy exists

Preview mode proves *what a flow would do*. Explain mode answers *what does this flow mean*. But
both need the same underlying graph of calls and the same captured execution metadata. Hierarchy
mode exposes that graph directly so that:

1. **Explain can stay small.** Explain no longer has to carry `executionTrace`, `externalCalls`,
   `dryRunLogs`, or `metrics`. It consumes a `HierarchyReport` and derives prose and diagrams from
   it.
2. **UIs and agents can render the graph as data.** A `HierarchyReport` is JSON-serializable and
   cycle-safe, so a frontend can draw a tree, filter calls, or compute coverage without parsing
   markdown.
3. **There is one source of truth for the call graph.** Both Explain and Preview reuse the same
   stage pipeline that collects the `astTree`, so a helper reused by three transactions appears
   once in the hierarchy and is referenced from every caller.

## What Hierarchy is not

- **Not a natural-language document.** For prose and budgeted markdown, use
  [Explain Mode](explain-mode.md).
- **Not a full production run.** It does not connect to Temporal workers; it executes the DSL
  lambdas locally like Preview. Use [Run mode](runtime.md) for production execution.
- **Not static analysis.** It runs the same `preview(...)` / `execute(...)` lambdas as Preview, so
  it can fail with real exceptions and records real (mocked) side effects.

## How Hierarchy works

1. **Entry point.** Hierarchy is invoked with the name of one DSL entity — usually a Process:
   `dslRuntime.hierarchy("UnreliableApiSuccess", ctx)` or `POST /api/dsl/hierarchy/{name}`.
2. **Execution mode is `HIERARCHY`.** `Context.mode()` is set to `ExecutionMode.HIERARCHY`. Runners
   treat `HIERARCHY` like `PREVIEW` for lambda selection (they run `effectivePreview()` / the
   default `execute()` logic), so no special lambda implementation is required.
3. **The same collector stages run as Preview.** Metrics, call-tree (`astTree`), dry-run logs,
   execution trace, faking, and external-call recording all execute. The only differences from
   Preview are that there is no result cache and the report stage builds a `HierarchyReport`.
4. **Graph-shaped result.** The report for the entry entity carries `children`, each of which is
   also a `HierarchyReport`. Cycles are detected and terminated by the call-tree collector, so the
   graph is always finite.
5. **Diagrams are derived on demand.** Each node can render itself to Mermaid, PlantUML, or BPMN
   from its own `dslDescriptor`, `externalCalls`, `callCounts`, and `hasCompensation` fields via
   `HierarchyDiagrams`.

For `UnreliableApiDsl.java` (`backend/dsl-starter/dsl-examples/src/dsl/`), calling hierarchy on
`UnreliableApiSuccess` walks:

```
UnreliableApiSuccess (process)
  └─ unreliableApiTxResilient (transaction)
       └─ unreliableApi (helper)
```

and returns one report node per entity in that order.

## The HierarchyReport

```java
public record HierarchyReport(
    @NonNull String name,
    @NonNull String description,
    @NonNull List<String> executionTrace,
    @NonNull List<Map<String, Object>> externalCalls,
    @NonNull Map<String, Integer> callCounts,
    boolean hasCompensation,
    @Nullable ExecutableDescriptor executableDescriptor,
    @Nullable DslDescriptor dslDescriptor,
    @Nullable CallNode astTree,
    @NonNull List<Map<String, Object>> dryRunLogs,
    @Nullable PreviewMetricsSnapshot metrics,
    @Nullable List<ErrorResponse> errors,
    @NonNull List<HierarchyReport> children,
    @Nullable String mermaidDiagram) {

  public @NonNull String toMermaid() { return HierarchyDiagrams.mermaid(this); }
  public @NonNull String toPlantUml() { return HierarchyDiagrams.plantUml(this); }
  public @NonNull String toBpmn() { return HierarchyDiagrams.bpmn(this); }
}
```

| Field | Purpose |
|-------|---------|
| `name` | DSL entity name. |
| `description` | Short type-prefixed label, e.g. `Process: UnreliableApiSuccess`. |
| `executionTrace` | Flat, ordered list of steps, including nested helper/transaction calls. |
| `externalCalls` | Captured JDBC, HTTP, MQ, Feign, and other side effects observed during the run. |
| `callCounts` | Aggregated counts by call type, e.g. `database=2, http=1`. |
| `hasCompensation` | Whether the entity declares compensation logic. |
| `executableDescriptor` / `dslDescriptor` | Registry descriptors for the entity. |
| `astTree` | Nested call-tree AST produced by the call-tree collector. |
| `dryRunLogs` | Log events captured while the entity ran in dry-run mode. |
| `metrics` | Optional performance/diagnostics snapshot. |
| `errors` | Non-fatal errors collected during the run. |
| `children` | `HierarchyReport` nodes for every distinct callee, in call order. |
| `mermaidDiagram` | Pre-rendered root Mermaid diagram ( Explain and UI consumers may also use `toMermaid()` for the whole graph). |

## JSON output shape

```json
{
  "name": "UnreliableApiSuccess",
  "description": "Process: UnreliableApiSuccess",
  "executionTrace": ["started: UnreliableApiSuccess", "called transaction: unreliableApiTxResilient"],
  "externalCalls": [{"type": "http", "target": "payment-api", "operation": "POST /pay"}],
  "callCounts": {"http": 1},
  "hasCompensation": true,
  "astTree": { "name": "UnreliableApiSuccess", "kind": "PROCESS", "children": [...] },
  "dryRunLogs": [...],
  "metrics": {...},
  "errors": [],
  "children": [
    {
      "name": "unreliableApiTxResilient",
      "description": "Transaction: unreliableApiTxResilient",
      ...
    }
  ],
  "mermaidDiagram": "graph TD\n  ..."
}
```

The graph is deduped by node name and cycle-safe: a helper called from two transactions appears
once in the top-level walk, and a back-edge is not re-queued.

## When to use Hierarchy

- **Before generating Explain markdown.** Explain mode runs hierarchy internally and maps each
  node to an `ExplainReport`; call hierarchy explicitly when you need the raw graph.
- **UI call-tree / dry-run panels.** The admin UI can render `astTree`, `externalCalls`, and
  `dryRunLogs` directly from a hierarchy response.
- **Documentation tools that want structured data** rather than a bounded markdown document.

Use [Explain Mode](explain-mode.md) when the goal is a human-readable, budgeted document; use
[Preview Mode](preview-mode.md) when you also need the actual business output and success flag.

## Relationship to other modes

The three safe/local modes form a capability chain:

```
Hierarchy → Explain → Preview
```

- **Hierarchy** gives the structured call graph and captured execution metadata.
- **Explain** consumes a hierarchy report and adds natural-language descriptions and per-node
  diagrams, then applies a character budget.
- **Preview** executes the same graph under `ExecutionMode.PREVIEW` and returns a `PreviewReport`
  that includes the actual output value, success flag, and all the same trace/call data.

## Current state and gaps

- `HierarchyReport`, `HierarchyDiagrams`, `HierarchyAccumulator`, and `HierarchyAccumulators` are
  the renamed versions of the former `ExplainGraph*` classes. The rename is in progress.
- `HierarchyDslPipe` and `HierarchyReportStage` are being extracted from the old Explain pipe.
- `ExecutionMode.HIERARCHY` and `DslRuntime.hierarchy()` still need to be added.
- The REST endpoint `POST /api/dsl/hierarchy/{name}` and the BFF proxy route
  `frontend/admin-ui-plugin/server/api/v1/dsl/hierarchy/[name].post.ts` still need to be added.
- `ExplainDslPipe` will be refactored to delegate to `HierarchyDslPipe` and map the result to
  `ExplainReport`, removing its direct dependency on the accumulator/diagram internals.

## Related docs

- [Explain Mode](explain-mode.md) — how hierarchy is turned into a budgeted markdown document.
- [Preview Mode](preview-mode.md) — the execution machinery (pipes, stages, `CallNode` AST) that
  hierarchy reuses.
- [Runtime Engine](runtime.md) — `DslRuntime`, `GlobalManager`, and the mode-agnostic REST surface.
- [ADR 0004 — Preview, dry-run, and Explain execution modes](../adr/0004-preview-dry-run-explain-modes.md)
- `docs/plans/hierarchy-mode-refactoring.md` — implementation plan for the rename and split.

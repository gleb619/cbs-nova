# Explain Mode

Explain mode is a **safe, local, graph-shaped documentation path** for DSL definitions. It reuses the same
`execute`/`preview` lambdas every other mode runs, but only to *walk* the call graph — Process → Transaction →
Helper/Function — and collect, per node, a human-readable description and a Mermaid fragment of what that node does.
The result is one markdown document covering every node a call transitively touches, deduped, cycle-safe, and
truncated to a caller-supplied character budget so it fits a context window.

## Why Explain exists

Preview mode ([Preview Mode](preview-mode.md)) proves *what a flow would do*. Explain mode answers a narrower,
cheaper question: *what does this flow mean*, for a reader — human or agent — who wants prose and a diagram instead
of an execution trace. It exists primarily so that:

1. **A coding agent can document a flow it just wrote or changed**, without hand-writing markdown, by calling explain
   on the entry-point Process and getting back a doc for every transaction/helper it reaches.
2. **A budget is respected.** Explain output is meant to be pasted into an LLM context window or a PR description, so
   it must stop cleanly at a character limit — a whole node short of the limit, never a half-cut node.
3. **The graph is deduped and cycle-safe.** A helper called from two transactions is explained once; a call cycle is
   detected and marked, not infinitely recursed.

## What Explain is not

- **Not a second implementation of business logic.** It calls the same `explainLogic`/`preview(...)` lambdas the
  DSL author already wrote (or the default derived from `execute`), it does not re-derive behavior statically from
  source.
- **Not a full execution report.** It carries no `externalCalls`, `dryRunLogs`, or `metrics` — for that, use
  [Preview Mode](preview-mode.md).
- **Not side-effecting.** Traversal never runs real `execute`; discovering a node's callees rides on the same
  `astTree: CallNode` collection Preview already produces (via `preview(...)`), not a second execution.

## How Explain works

1. **Entry point.** Explain is invoked with the name of one DSL entity (a Process, in the common case) — e.g.
   `dslRuntime.explain("UnreliableApiSuccess", ...)`.
2. **Per-node report.** For the entity itself, its own `explainLogic` is resolved to produce a description + a local
   Mermaid fragment (`ExplainReport`).
3. **Callee discovery reuses Preview's AST.** Rather than statically parsing DSL source for `runTransaction`/
   `runHelper` calls, Explain runs the node once through `preview(...)` (side-effect-free by contract) and reads the
   resulting `astTree: CallNode` to find what it called.
4. **Recursion with a cycle guard.** Each discovered callee is visited once: a `LinkedHashMap<String, ExplainReport>`
   keyed by entity name is both the accumulator and the cycle guard — insertion order gives call order for rendering,
   and a node already present (or currently mid-visit, for a true cycle) is skipped rather than re-explained.
5. **Budget enforcement is graph-aware.** The character budget applies to the whole document, not one node's string:
   nodes are emitted whole, in call order, until the next node would not fit — then a
   `... N more nodes omitted, budget exhausted` marker closes the document. No node is ever cut mid-sentence.

For `UnreliableApiDsl.java` (`backend/dsl-starter/dsl-examples/src/dsl/`), explaining `UnreliableApiSuccess`
walks:

```
UnreliableApiSuccess (process)
  └─ runTransaction("unreliableApiTxResilient")
       └─ runHelper("unreliableApi")
```

and produces one markdown section per node, in that order.

## The Explain graph report

`ExplainReport` is itself the graph, not a flat leaf wrapped by something else: each report carries `children`, the
links to the `ExplainReport` of every entity it calls. A traversal is just the root report plus whatever `children`
reach transitively — no separate node/edge collection to keep in sync. The record stays pure data plus trivial
navigation; graph operations (merge, truncation, rendering) live in a companion `ExplainReports` utility so the
record itself doesn't accumulate algorithmic logic:

```java
public record ExplainReport(String name, String description, String mermaid,
        List<ExplainReport> children) {
  ExplainReport(String name, String description, String mermaid); // children = List.of()
  ExplainReport withChildren(List<ExplainReport> children);
  ExplainReport addChild(ExplainReport child);
}

public final class ExplainReports {
  static ExplainReport merge(ExplainReport left, ExplainReport right);  // single-node concat; children merged by name
  static ExplainReport truncateTo(ExplainReport report, int budgetChars); // truncates that node's own text only
  static String toMarkdown(ExplainReport root, int budgetChars);         // graph-aware, budget-bounded traversal
}
```

`ExplainReports.merge`/`truncateTo` still operate on one node's own `description`/`mermaid` — they never look past
`children`. `toMarkdown` is the graph-level operation: it walks `root` and `children` breadth-first, accumulating
visited nodes in a `LinkedHashMap<String, ExplainReport>` keyed by name. That map is simultaneously the traversal
order (insertion order = call order) and the cycle guard (a name already present is not re-queued), which is exactly
what lets the same map replace the separate "nodes" registry an earlier design of this doc proposed — see
[How Explain works](#how-explain-works), step 4.

Because the character budget can't be sized correctly until the whole graph is known — a helper reused by three
transactions should only count once, a cycle must terminate, and the last node that fits must end exactly on a node
boundary — truncation is a property of the traversal (`toMarkdown`), not of any one report's constructor. A single
`ExplainReport` never knows how much of the eventual document it gets to keep; only the walk does.

## Markdown output shape

```markdown
## UnreliableApiSuccess
<description + mermaid>

## unreliableApiTxResilient
<description + mermaid>

## unreliableApi
<description + mermaid>
```

Each `## <name>` heading + body is exactly one graph node's own `description`/`mermaid`, in the order `toMarkdown`
visited it. A caller that wants the type annotation shown in earlier drafts of this doc (`(process)`, `(transaction)`,
`(helper)`) folds it into `name` when building the node — `ExplainReport` itself stays type-agnostic, since type is a
`DslDescriptor` concern the traversal, not the leaf record, owns. If the budget runs out before every reachable node
fits, `toMarkdown` closes the document with one trailing marker, e.g. `... 2 more nodes omitted, budget exhausted` —
never a node cut mid-sentence.

## When to use Explain

- **Documenting a flow for humans or reviewers**, especially after an agent authors or changes DSL.
- **Feeding a flow's behavior into another LLM call** where a bounded, budgeted markdown doc is cheaper than a full
  `PreviewReport`.
- **Diagram generation** — the Mermaid fragments compose into one call-graph diagram per document.

Use [Preview Mode](preview-mode.md) instead when the goal is to verify actual execution (output shape, external
calls, dry-run logs) rather than to describe the flow.

## Relationship to other modes

- **Run mode** executes the generated Temporal workflows and activities against a real cluster.
- **Preview mode** executes the same DSL logic locally and returns a full execution report. Explain's traversal reuses
  Preview's `astTree: CallNode` collection to discover callees without a second execution mechanism.
- **Explain mode** is a read-only walk of the call graph that never runs real `execute`, producing prose and diagrams
  sized to a budget instead of an execution trace.

## Current state and gaps

- `ExplainReport` (`cbs.nova.dsl.model.ExplainReport`, dsl-api) is now graph-shaped: it carries `children` and can
  render a whole reachable graph via `toMarkdown(budgetChars)`. What is *not* done yet is wiring a traversal that
  populates `children` from real callee discovery — today each entity's `explainLogic` still produces a single leaf
  report per call, same as before.
- The deprecated flat explain pathway (`GlobalManager.explain(name, ctx, budgetChars)` + `ExplainReportFactory`) was
  removed per `docs/plans/T513-retire-deprecated-explain-pathway.md`. The only remaining pathway is the live
  pipe/stage one (`ExplainDslPipe` + `ExplainReportStage`/`ExplainBudgetStage`) which already executes the real
  `preview`/`explain` lambda and collects an `astTree`, but produces one `ExplainTraceReport` for the top-level call,
  not a graph node per callee. Note
  `ExplainTraceReport` is a separate, unrelated record (full execution trace: `astTree`, `externalCalls`, `metrics`,
  `errors`) and is out of scope for this graph refactor.
- `ExplainReport.merge` still has zero callers in the traversal itself — nothing yet walks `preview`'s `astTree` to
  call `addChild`/`withChildren` and build the graph this doc describes.
- Whether `ProcessDslObject`/`FunctionDslObject` carry `explainLogic` symmetrically with
  `TransactionDslObject.explainLogic` needs confirming before the per-node-type dispatch above can be implemented.

## Related docs

- [Preview Mode](preview-mode.md) — the execution machinery (pipe/stage chain, `CallNode` AST, `ExecutionTreeStage`)
  Explain's traversal reuses.
- [ADR 0004 — Preview, dry-run, and Explain execution modes](../adr/0004-preview-dry-run-explain-modes.md)
- [Authoring DSL Flows](authoring.md) — `dslRuntime.explain(...)` call shape.
- `docs/plans/explain-mode-redesign.md` — prior redesign of the single-entity builder/lambda plumbing.
- `docs/plans/T513-retire-deprecated-explain-pathway.md` — removal of the flat legacy pathway this design supersedes.

# Explain Mode

Explain mode is a **safe, local, graph-shaped documentation path** for DSL definitions. It builds on
[Hierarchy Mode](hierarchy-mode.md): it runs the same DSL graph as Preview, produces a
`HierarchyReport`, then maps each node to a human-readable description and an optional Mermaid
schema. The result is one markdown document covering every node a call transitively touches,
deduped, cycle-safe, and truncated to a caller-supplied character budget so it fits a context window.

## Why Explain exists

Preview mode ([Preview Mode](preview-mode.md)) proves *what a flow would do*. Hierarchy mode
([Hierarchy Mode](hierarchy-mode.md)) gives the structured graph and execution metadata. Explain mode
answers a narrower, cheaper question: *what does this flow mean* — for a reader (human or AI agent)
who wants to understand how a Process/Transaction/Helper works and what it integrates with,
without re-reading the source. It exists primarily so that:

1. **A reader — human or AI agent — gets a single doc explaining how the object works and what
   it integrates with.** For each reachable node, the doc names the entity, describes its role
   in the call chain, and (when useful) renders a Mermaid schema of the node itself.
2. **A coding agent can document a flow it just wrote or changed**, without hand-writing markdown,
   by calling explain on the entry-point Process and getting back a doc for every
   transaction/helper it reaches.
3. **A budget is respected.** Explain output is meant to be pasted into an LLM context window or a
   PR description, so it must stop cleanly at a character limit — a whole node short of the limit,
   never a half-cut node.
4. **The graph is deduped and cycle-safe.** A helper called from two transactions is explained once;
   a call cycle is detected and marked, not infinitely recursed.

## What Explain is not

- **Not a second implementation of business logic.** It calls the same `explainLogic`/`preview(...)`
  lambdas the DSL author already wrote (or the default derived from `execute`), it does not
  re-derive behavior statically from source.
- **Not a full execution report.** It carries no `executionTrace`, `externalCalls`, `dryRunLogs`, or
  `metrics` — those live in the underlying [Hierarchy Mode](hierarchy-mode.md). For the actual
  business output and success flag, use [Preview Mode](preview-mode.md).
- **Not side-effecting.** Traversal never runs real `execute`; discovering a node's callees rides on
  the same `astTree: CallNode` collection Preview already produces (via `preview(...)`), not a
  second execution.

## How Explain works

1. **Entry point.** Explain is invoked with the name of one DSL entity (a Process, in the common
   case) — e.g. `dslRuntime.explain("UnreliableApiSuccess", ...)` or
   `POST /api/dsl/explain/{name}`. The introspection surface exposes the call's I/O contract:
   `GET /api/dsl/schemas/{name}?mode=explain` resolves the same construct chain as
   `mode=preview` (process → transaction → helper → function) and returns a `ConstructSchemaDto`
   whose input fields match preview (the explain call takes the same body), while `outputType`
   is `ExplainReport` and `outputSchema` describes the report record shape (recursive
   `children`). The report itself comes from `dslRuntime.explain(...)` /
   `POST /api/dsl/explain/{name}`.
2. **Hierarchy run first.** Explain mode internally runs the entity through
   `ExecutionMode.HIERARCHY`. This produces a `HierarchyReport` graph carrying the call tree,
   external calls, dry-run logs, metrics, and errors for every reachable node.
3. **Per-node mapping.** For each `HierarchyReport` node, Explain builds an `ExplainReport` node:
   `name` is preserved; `description` is derived from the entity descriptor or helper catalog
   (covers what the node does and what it integrates with); `mermaid` is rendered from the node's
   own fields via `HierarchyDiagrams.mermaidNode(...)` and may be empty when a schema adds no
   value; `children` are mapped recursively.
4. **Budget enforcement is graph-aware.** The character budget applies to the whole document, not
   one node's string: nodes are emitted whole, in call order, until the next node would not fit —
   then a `... N more nodes omitted, budget exhausted` marker closes the document. No node is ever
   cut mid-sentence. Token caps on `name`, `description`, and `mermaid` are also applied before the
   character budget is measured.

For `UnreliableApiDsl.java` (`backend/dsl-starter/dsl-examples/src/dsl/`), explaining
`UnreliableApiSuccess` walks:

```
UnreliableApiSuccess (process)
  └─ runTransaction("unreliableApiTxResilient")
       └─ runHelper("unreliableApi")
```

and produces one markdown section per node, in that order.

## The Explain graph report

`ExplainReport` is itself the graph, not a flat leaf wrapped by something else: each report carries
`children`, the links to the `ExplainReport` of every entity it calls. A traversal is just the root
report plus whatever `children` reach transitively — no separate node/edge collection to keep in
sync. The record stays pure data plus trivial navigation; graph operations (merge, truncation,
rendering) live in a companion `ExplainReports` utility so the record itself doesn't accumulate
algorithmic logic:

```java
public record ExplainReport(String name, String description, String markdown,
        List<ExplainReport> children) {
  ExplainReport(String name, String description, String markdown); // children = List.of()
  ExplainReport withChildren(List<ExplainReport> children);
  ExplainReport addChild(ExplainReport child);
}

public final class ExplainReports {
  static ExplainReport merge(ExplainReport left, ExplainReport right);
  static String toMarkdown(ExplainReport root, int budgetChars);
}
```

`ExplainReports.merge` still operates on one node's own `description`/`markdown` — it never looks
past `children`. `toMarkdown` is the graph-level operation: it walks `root` and `children`
breadth-first, accumulating visited nodes in a `LinkedHashMap<String, ExplainReport>` keyed by name.
That map is simultaneously the traversal order (insertion order = call order) and the cycle guard
(a name already present is not re-queued).

Because the character budget can't be sized correctly until the whole graph is known — a helper
reused by three transactions should only count once, a cycle must terminate, and the last node that
fits must end exactly on a node boundary — truncation is a property of the traversal
(`toMarkdown`), not of any one report's constructor. A single `ExplainReport` never knows how much
of the eventual document it gets to keep; only the walk does.

## Markdown output shape

```markdown
## UnreliableApiSuccess
<description>

<mermaid schema for the node — optional, omitted when nothing useful to show>

## unreliableApiTxResilient
<description>

<mermaid schema for the node — optional, omitted when nothing useful to show>

## unreliableApi
<description>
```

Each `## <name>` section holds the node's `description` and an optional Mermaid schema (rendered
only when it adds value for the reader). Sections appear in the order `toMarkdown` visited them.
A caller that wants the type annotation shown in earlier drafts of this doc (`(process)`,
`(transaction)`, `(helper)`) folds it into `name` when building the node — `ExplainReport` itself
stays type-agnostic, since type is a `DslDescriptor` concern the traversal, not the leaf record,
owns. If the budget runs out before every reachable node fits, `toMarkdown` closes the document
with one trailing marker, e.g. `... 2 more nodes omitted, budget exhausted` — never a node cut
mid-sentence.

## When to use Explain

- **Documenting a flow for humans or reviewers**, especially after an agent authors or changes DSL.
- **Feeding a flow's behavior into another LLM call** where a bounded, budgeted markdown doc is
  cheaper than a full `PreviewReport` or `HierarchyReport`.
- **Diagram generation** — the Mermaid fragments compose into one call-graph diagram per document.

Use [Preview Mode](preview-mode.md) instead when the goal is to verify actual execution (output
shape, external calls, dry-run logs) rather than to describe the flow. Use
[Hierarchy Mode](hierarchy-mode.md) when you need the raw structured graph rather than a markdown
summary.

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

**Run mode** executes the generated Temporal workflows and activities against a real cluster.
**Preview mode** executes the same DSL logic locally and returns a full execution report. **Explain
mode** is a read-only documentation layer on top of the hierarchy graph that never runs real
`execute`.

## Current state

- `ExplainReport` (`cbs.nova.dsl.model.ExplainReport`, dsl-api) is graph-shaped: it carries
  `children` and can render a whole reachable graph via `ExplainReports.toMarkdown(budgetChars)`.
- The hierarchy split is wired: `HierarchyDslPipe` produces the `HierarchyReport` graph and
  `ExplainDslPipe` delegates to it, mapping each node to an `ExplainReport` via `ExplainMapper`
  and applying token caps plus the character budget with `ExplainBudget`. Explain carries no
  `executionTrace`/`externalCalls`/`dryRunLogs`/`metrics` of its own — those stay on the
  hierarchy report.
- `ExplainReports` still has a `@Deprecated` marker and a TODO to move to a `util` package; that
  cleanup is tracked separately from the hierarchy split.

## Related docs

- [Hierarchy Mode](hierarchy-mode.md) — the structured graph that Explain consumes.
- [Preview Mode](preview-mode.md) — the execution machinery (pipe/stage chain, `CallNode` AST,
  `ExecutionTreeStage`) Explain's traversal reuses.
- [Runtime Engine](runtime.md) — `DslRuntime`, `GlobalManager`, and the mode-agnostic REST surface.
- [ADR 0004 — Preview, dry-run, and Explain execution modes](../adr/0004-preview-dry-run-explain-modes.md)
- [Authoring DSL Flows](authoring.md) — `dslRuntime.explain(...)` call shape.

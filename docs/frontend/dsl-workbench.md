# DSL Workbench

The **DSL Workbench** is the admin UI for authoring cbs-nova DSL definitions. It targets non-developer domain experts as well as engineers, so the interface favors forms, structured input, and immediate feedback over raw source editing.

A DSL definition is one of four constructs: **Process**, **Transaction**, **Function**, or **Helper**. Processes orchestrate Transactions, Helpers, and Functions. Transactions are single retryable actions. Functions and Helpers are lightweight reusable logic. See [DSL Constructs](../dsl/constructs.md) and [Authoring DSL Flows](../dsl/authoring.md) for the underlying model.

## 1. Construct explorer

The left sidebar lists all DSL constructs in the current module.

- **Grouping** — items are grouped by construct type: Processes, Transactions, Functions, Helpers.
- **Search** — filter by name, task queue, or referenced helper/transaction.
- **Status badges** — each row shows the effective state:
  - `Draft` — unsaved changes or never published.
  - `Valid` — passes compiler validation.
  - `Invalid` — has compiler errors.
  - `Published` — saved to the DSL module source.
- **Create action** — a single "New" button lets the user pick the construct type and opens it in the editor.
- **Selection** — clicking a construct opens it. Unsaved changes in the current editor are kept until explicitly discarded.

## 2. Split editor layout

The workbench uses a fixed three-zone layout:

| Zone | Purpose |
|------|---------|
| **Sidebar** | Construct explorer (see above). |
| **Metadata panel** | Top section of the main area: name, version, types, task queue, retry policy. |
| **Body editor** | Bottom section of the main area: form-based structure plus the `execute` code view. |

The metadata panel can be collapsed to give the body editor more room. A status bar at the bottom shows the last validation result and the current save/publish state.

## 3. Metadata panel

Metadata fields depend slightly on the construct type.

Common fields for every construct:

- **Name** — unique identifier; immutable after first publish for a given construct.
- **Version** — optional human-readable label (e.g., `1.0.0`).

Process and Transaction fields:

- **Task queue** — the Temporal task queue the generated worker will listen on.
- **Retry policy** — `maxAttempts`, `initialInterval`, `backoffCoefficient`, `maximumInterval`.
- **Start-to-close timeout** — activity timeout for Transactions.

Input/output specification (one of two modes):

- **Typed** — select an existing `@Json` record class for input and another for output.
- **Parameter-based** — define a flat schema of fields (`string`, `number`, `boolean`, `date`, `map`, `list`).

The panel validates that typed records are visible to the DSL module and that parameter names are unique. Function and Helper editors omit task queue, timeout, and retry policy because they run locally.

## 4. Body editor

The body editor has two tabs:

### Structure tab

A form-driven outline of the definition:

- For a **Process**: ordered steps, where each step is a helper call, function call, transaction call, or local variable assignment.
- For a **Transaction**: the same step types, plus the optional compensation block.
- For a **Function** or **Helper**: a single sequence of steps.

Each step row shows the target name, input mapping, and assigned output variable. Steps can be reordered by drag and drop, duplicated, or deleted.

### Code tab

An editable code view for the `execute` block. The editor is pre-filled from the structure tab and kept in sync:

- Editing the structure rewrites the code skeleton.
- Editing the code updates the structure when the user switches back, if the code is parseable.
- Unparseable code keeps the structure tab in its last known good state and shows an error.

The code view supports syntax highlighting for the DSL builder calls (`runHelper`, `runTransaction`, `fail`, `log`, `complete`, `withBody`) and basic autocomplete for variable names and field accessors.

## 5. Inline validation feedback

Validation runs automatically after each meaningful change and on explicit request.

Feedback is shown in three places:

1. **Inline markers** — red underlines and tooltips on fields, step rows, and code lines.
2. **Problems panel** — a collapsible drawer listing every error and warning with a clickable location.
3. **Status bar** — a short summary such as "3 errors" or "Valid".

The workbench delegates validation to the DSL compiler. Typical messages include:

- Unknown helper or transaction name.
- Type mismatch in a `runHelper` or `runTransaction` call.
- Missing parameter referenced in `execute` but not declared in metadata.
- Compensation block invoking a Transaction or Process.
- Function calling a Process or Transaction.
- Duplicate construct name.

Warnings appear for discouraged patterns, such as a Process without a compensation block when one of its Transactions declares compensation.

## 6. Save / publish flow

The workbench distinguishes between local draft state and committed source.

- **Save draft** — persists the current editor state to a local workspace so the author can resume later. Drafts do not affect the DSL module.
- **Validate** — runs the DSL compiler against the current definition and reports errors.
- **Publish** — writes the construct to the DSL module source file (`dsl-examples/src/*.java`) only if validation passes.
- **Unpublish** — removes a published construct from source. Unpublish is blocked if another published construct references it.

After publish, the backend rebuilds the DSL module. The workbench polls for build status and updates the construct badge accordingly. If the build fails, the error is shown in the problems panel and the construct remains marked `Invalid`.

## 7. Helper and transaction reference picker

When a step references a Helper, Function, or Transaction, the user opens a picker instead of typing the name.

The picker shows:

- **Name and type** — e.g., `riskAssessment` (Helper) or `KYC_CHECK` (Transaction).
- **Input/output signature** — typed records or parameter schema.
- **Availability** — only references that are legal from the current construct are selectable. For example, a Function editor cannot pick a Transaction.
- **Description** — sourced from the `explain` metadata of Helpers when available.

After selection, the workbench proposes an input mapping:

- **Auto-resolve** — map fields from the current context body when names and types match.
- **Explicit map** — open a small mapping grid to connect each target input field to a constant, context field, or prior step output.

The picker also supports creating a new Helper or Function stub directly from the body editor when a needed reference does not yet exist.

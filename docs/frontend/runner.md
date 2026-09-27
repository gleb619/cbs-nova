# Runner UI

The runner UI lets operators and flow authors execute a selected DSL definition and inspect the result. It is the main surface for the backend's three operational modes: Preview, Run, and Explain.

## Purpose

- Validate DSL definitions quickly without touching the live Temporal cluster.
- Trigger production executions against Temporal with clear confirmation.
- Generate human-readable explanations of a definition's flow.
- Surface typed inputs, outputs, call trees, live dry-run logs, and errors in one place.

## Page structure

Route: `/runner` (`frontend/admin-ui-plugin/app/pages/runner.vue`). The page is built from components in the `@cbs/components` package (`frontend/components/src/components/runner/`), with shared state in `useRunner` (`app/composables/useRunner.ts`).

From top to bottom:

1. **Page header** — title "Runner" plus the `StatusIndicator` status pill.
2. **Control row** — `RunnerDefinitionSelector` combobox, `RunnerModeSwitcher` segmented control, and the Submit / Compare-with-previous / Reset buttons.
3. **Two-column grid** (`lg:grid-cols-2`) — Input card on the left, Output card on the right.
4. **Preview-mode notice** — a full-width "Faking external calls" info card shown only in Preview mode.

The selected definition and mode are synced to the URL query string (`?name=`, `?mode=`) so a run can be shared or refreshed.

## Mode switcher

`RunnerModeSwitcher` is a segmented `radiogroup` control with three modes (`RunnerMode = 'preview' | 'run' | 'explain'` in `components/src/types/runner.ts`):

| Mode | Backend call | Effect |
|------|--------------|--------|
| **Preview** | `preview()` in `useDslApi` → BFF dry-run | Dry-run execution directly against the DSL runtime; no Temporal cluster is used. Sends an `X-Request-Id` header used as the SSE log-stream key. |
| **Run** | `run()` in `useDslApi` → BFF run | Live execution against the configured Temporal cluster. Sends an `Idempotency-Key` header. |
| **Explain** | `explain()` in `useDslApi` → BFF explain | Dry-run execution that also returns a description and report chain. |

There is no Hierarchy mode in the runner. Hierarchy is available only as a body-editor tab in the DSL workbench (`dsl/HierarchyTab.vue`, fed by `dslApi.hierarchy()`).

All requests share the `{ body, metadata }` envelope; the runner does not expose a metadata editor — it passes `metadata: undefined`.

Switching modes does not clear output; output is cleared on definition selection and via the Reset button. Input form values persist across mode switches so the same payload can be reused.

## Definition selector

`RunnerDefinitionSelector` lists all available DSL definitions registered in the backend registries. Selecting a definition loads its typed input record schema, rebuilds the input form, clears previous output, and updates the URL.

## Input form

`RunnerInputForm` is schema-driven from the selected definition's input JSON schema:

- When the schema has properties, one `InputField` is rendered per property. `InputField` renders:
  - `string` → text input,
  - `number` / `integer` → number input,
  - `boolean` → checkbox,
  - everything else (array, object, enum, date) → a plain JSON textarea.
- Required fields are marked with a red `*` and validated on blur ("is required").
- When the definition has no input record or an empty schema, the form falls back to a single freeform JSON textarea for the whole payload.

The form payload is assembled into the `body` field of the envelope sent to the backend.

## Confirmation step for Run mode

Because Run mode executes against a live Temporal cluster, the UI inserts a confirmation step (`RunConfirmationModal`):

1. The user fills the input form and clicks **Run**.
2. A modal appears with the definition name, a summary of the input payload, a warning that this creates a real workflow execution, and a required checkbox ("I understand this will run the workflow").
3. The user must check the box and click **Confirm** before the request proceeds.

Preview and Explain modes skip this confirmation.

The dialog offers a "Don't ask again during this session" preference stored in session storage under `skip-run-confirm`. Note: the current `useRunner` implementation always shows the modal in run mode — the stored preference is not consulted.

## Output panel

`RunnerOutputPanel` sits on the right side and is organized into a tab bar:

| Tab | Visibility | Content |
|-----|------------|---------|
| **Result** | Always | The typed `body` returned by the backend, rendered as a plain `<pre>` JSON view. |
| **Metadata** | Always | The `metadata` map returned in the result. |
| **Errors** | Always | Failures via the shared `ErrorList` component with error-code badges. |
| **Call Tree** | Preview / Explain | Recursive AST tree (`CallTreeTab` / `CallTreeNode`) built from `output.astTree`; nodes are PROCESS / TRANSACTION / HELPER / FUNCTION calls. |
| **Logs** | Preview / Explain | Live dry-run log lines streamed over SSE while running; inline `dryRunLogs` take over after completion. Level color chips, line count, and "Copy all" (`DryRunLogsTab`). |
| **External Calls** | Preview / Explain | External system calls observed during the dry run, with a summary badge (`ExternalCallsTab`, `ExternalCallsBadge`). |
| **Diff** | Preview with baseline | Difference between the current run and a saved baseline: AST diff (`ASTDiffNode`, `DiffLine`) and metrics diff (`MetricsDiffView`, `MetricsDiffTable`). |

"Compare with previous" snapshots the current output as `baselineOutput` and re-runs; the Diff tab then appears.

## Live log streaming (SSE)

`useDryRunLogStream` (`app/composables/useDryRunLogStream.ts`) opens a native `EventSource` connection to `/api/v1/dsl/dry-run/{traceId}/logs`, keyed by the `X-Request-Id` sent with the preview/explain request. It exposes `status` (`idle | connecting | open | error`) and emitter-based `onLogLine` / `onOpen` / `onError` callbacks. When SSE is unsupported, the Logs tab falls back to the inline `dryRunLogs` returned with the response. There is a single connection at a time.

## Explain mode output

Explain mode renders `ExplainOutput` above the standard tabs:

- **Description** — the natural-language summary from the `ExplainReport`, listing main steps, branches, and external calls.
- **Report chain** — `ExplainReportChain` / `ExplainReportList` walk the nested reports.
- **Raw views** — `ExplainRawView`, `ExplainMarkdownView`, and `ExplainDiffView` expose the report in other formats.
- **Mermaid diagram** — the `mermaidDiagram` field is shown as raw text in a `<pre>` block. It is not rendered with Mermaid.js and there is no copy/zoom control.

## Status display

`StatusIndicator` in the page header shows the current execution state:

| State | Meaning |
|-------|---------|
| Idle | No request in progress. |
| Loading | Request sent, waiting for response. |
| Running | Workflow is active on Temporal (pulsing indicator). |
| Success | Execution completed successfully. |
| Failed | Execution returned an error or threw an exception. |

Run mode is awaited synchronously by `useRunner.submit()`: the request completes and the status settles to success or failed. The returned workflow ID is available in the `RunnerOutput` data; deep polling and diagnostics live in the execution details view ([execution-details.md](./execution-details.md)).

## Error handling

| Source | Handling |
|--------|----------|
| Form validation | Inline field errors; submit is blocked. |
| Network failure | Error surfaced in the Errors tab. |
| Backend validation / runtime exception | Errors tab with the validation message and error-code badge. |
| Temporal failure (Run mode) | Errors tab; the workflow ID from the response is available for follow-up in the executions view. |

## Best practices

- Always start with **Preview** to validate a DSL definition before switching to **Run**.
- Use **Explain** to generate descriptions and diagrams for runbooks or stakeholder review.
- Watch the **Logs** tab during Preview to see live execution traces via SSE.
- Use **Compare with previous** to spot behavioral drift between two Preview runs.
- Review the confirmation dialog payload carefully before confirming a live Run.

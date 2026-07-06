# Runner UI

The runner UI lets operators and flow authors execute a selected DSL definition and inspect the result. It is the main surface for interacting with the three operational modes of the backend: Preview, Run, and Explain.

## Purpose

- Validate DSL definitions quickly without touching the live Temporal cluster.
- Trigger production executions against Temporal with clear confirmation.
- Generate human-readable explanations and diagrams for documentation or review.
- Surface typed inputs, outputs, metadata, and errors in one place.

## Layout

The runner panel is split into three vertical regions:

| Region | Content |
|--------|---------|
| **Top bar** | Definition selector and mode switcher |
| **Left side** | Auto-generated input form |
| **Right side** | Output panel with tabs for result, metadata, and errors |

On smaller viewports the input form and output panel stack vertically.

## Mode switcher

A segmented control selects the operational mode for the next execution:

| Mode | Backend call | Effect |
|------|--------------|--------|
| **Preview** | `POST /api/dsl/preview/{name}` | Dry-run execution directly against the DSL runtime; no Temporal cluster is used. |
| **Run** | Starts the generated Temporal workflow | Live execution against the configured Temporal cluster. |
| **Explain** | `POST /api/dsl/explain/{name}` | Dry-run execution that also returns a description and diagram. |

The selected mode is persisted in the URL query string so a run can be shared or refreshed.

Only one mode is active at a time. Switching modes clears transient output but keeps the input form values so the same payload can be reused across modes.

## Definition selector

The top bar contains a combobox that lists all available DSL definitions registered in the backend registries. Selecting a definition:

- loads its typed input record schema,
- rebuilds the input form,
- clears previous output,
- updates the URL.

If the selected definition has no input record or only an empty record, the form collapses to a single submit button.

## Input form

The input form is auto-generated from the selected definition's typed input record. The frontend receives the JSON schema and renders controls for each field:

| JSON schema type | Rendered control |
|------------------|------------------|
| `string` | Text input |
| `number` / `integer` | Number input |
| `boolean` | Toggle switch |
| `enum` | Dropdown |
| `array` | Repeating field group with add/remove buttons |
| `object` | Nested fieldset |
| `date` / `datetime` | Date/time picker |

- Required fields are marked and validated before submission.
- Nested records are expanded inline using fieldsets.
- Arrays show an empty first row by default when the field is required.
- Validation errors from the form are displayed next to the offending field and block submission.

The form payload is assembled into the `body` field of the `Context` object sent to the backend:

```json
{
  "body": { /* generated from the form */ },
  "metadata": {
    "correlationId": "...",
    "startedFrom": "ui-runner"
  }
}
```

Metadata fields are editable through a collapsible "Advanced" section.

## Confirmation step for Run mode

Because Run mode executes against a live Temporal cluster, the UI inserts a confirmation step before the request is sent:

1. The user fills the input form and clicks **Run**.
2. A modal appears with:
   - the definition name,
   - the target environment/cluster label,
   - a summary of the input payload,
   - a clear warning that this will create a real workflow execution,
   - a required checkbox: "I understand this runs against Temporal".
3. The user must check the box and click **Confirm** before the request proceeds.

Preview and Explain modes do not show this confirmation step.

The confirmation dialog can be skipped for the current session if the user checks "Don't ask again during this session". This preference is stored in session storage, not persisted across browser sessions.

## Output panel

The output panel appears on the right side after an execution request finishes or fails. It is organized into tabs.

### Result tab

Displays the typed `body` returned by the backend. Object fields are rendered as a read-only structured view with copy-to-clipboard buttons for individual values and the whole payload. Primitive values are shown inline.

### Metadata tab

Shows the `metadata` map returned in the `Context`:

- execution trace entries,
- correlation ID,
- timing information,
- custom metadata added by the DSL definition.

Metadata is rendered as a key-value list or, when the value is an object/array, as a collapsible JSON tree.

### Errors tab

Appears only when the execution fails. It displays:

- a human-readable error message,
- the error category (validation, runtime, Temporal, network),
- the stack trace or trace ID when available,
- a link to the relevant execution details view for Run mode failures.

If the failure happened during a Run-mode Temporal execution, the panel also shows the Temporal workflow ID and run ID.

## Explain mode output

Explain mode adds two dedicated sections above the standard output tabs:

### Description

A natural language summary of the execution flow returned by the backend in the `ExplainReport`. It lists the main steps, branches, compensations, and external system calls in plain language.

### Mermaid diagram

The `mermaidDiagram` field from the report is rendered with Mermaid.js. The diagram shows:

- start and end nodes,
- Processes, Transactions, Helpers, and Functions as labeled nodes,
- sequence edges,
- gateways for branches,
- compensation paths where applicable.

A copy button lets the user copy the raw Mermaid source. The diagram container supports zoom and pan when it does not fit the panel.

## Status display

A status indicator in the output panel header shows the current execution state:

| State | Indicator | Meaning |
|-------|-----------|---------|
| Idle | Gray dot | No request in progress. |
| Loading | Spinning amber indicator | Request sent, waiting for response. |
| Success | Green check | Execution completed successfully. |
| Failed | Red cross | Execution returned an error or threw an exception. |
| Running (Run mode) | Pulsing blue indicator | Workflow is active on Temporal; poll for updates. |

For Run mode, the status also includes the Temporal workflow ID and a link to the execution details view once the workflow has started.

## Error handling

Errors can come from several layers. The runner handles each with a specific message and, when possible, recovery action:

| Source | Handling |
|--------|----------|
| Form validation | Inline field errors; submit is blocked. |
| Network failure | Retry button and a message with the HTTP status. |
| Backend validation | Error tab with the validation message and field path. |
| Runtime exception | Error tab with stack trace and trace ID. |
| Temporal failure | Error tab with workflow ID, run ID, and link to execution details. |

Long-running Run mode executions are not awaited synchronously. The UI starts the workflow, receives the workflow ID, and redirects or updates the status to "Running". Polling for completion is handled by the execution details view.

## Best practices

- Always start with **Preview** to validate a DSL definition before switching to **Run**.
- Use **Explain** to generate diagrams for runbooks or stakeholder review.
- Keep input payloads small and focused; use the metadata section for traceability.
- Review the confirmation dialog payload carefully before confirming a live Run.
- Copy the correlation ID from the metadata tab when reporting an issue.

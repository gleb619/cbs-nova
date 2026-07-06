# Execution Details UI

The **Execution Details** page lets users inspect every run of a DSL Process, Transaction, or Helper after it has been started in **Run**, **Preview**, or **Explain** mode. It surfaces the execution trace, current status, input/output payloads, generated diagrams, metadata, logs, and compensation behavior in one place.

## Execution list

The list is the entry point to observability. Each row represents one execution of a DSL entity and exposes the most important facts at a glance.

### Columns

| Column | Description |
|--------|-------------|
| **ID** | Correlation or execution identifier. Click to open the detail view. |
| **Entity** | Name of the Process, Transaction, or Helper/Function that was executed. |
| **Mode** | `Run`, `Preview`, or `Explain`. |
| **Status** | Current lifecycle state. See status badges below. |
| **Started** | Absolute timestamp when the execution began. |
| **Duration** | Wall-clock time from start to now or completion. |
| **Retries** | Number of retry attempts for the top-level execution or its failing step. |
| **Triggered by** | User, CI job, or parent Process/Transaction. |

### Status badges

- **Pending** — waiting for worker capacity, input, or a scheduled start time.
- **Running** — currently executing; at least one step is in progress.
- **Completed** — finished successfully with a final output context.
- **Failed** — terminated with an unhandled error or exhausted retries.
- **Compensated** — the main flow failed and the Saga compensation path completed.

Additional transient badges may appear inside the trace, such as `Retrying`, `Cancelled`, or `Timed out`.

### Filters and search

Users can narrow the list by:

- **Status** — one or more lifecycle states.
- **Mode** — `Run`, `Preview`, `Explain`.
- **Entity type** — `Process`, `Transaction`, `Helper`, `Function`.
- **Entity name** — free-text or autocomplete.
- **Time range** — start/end timestamps.
- **Correlation ID** — exact or partial match.
- **Tags/Metadata** — key/value pairs such as environment, tenant, or CI pipeline.

## Detail view

The detail view is organized into a summary header, a step-by-step trace, and tabbed content below.

### Summary header

The header repeats the execution ID, entity name, mode, status badge, start time, duration, and retry count. It also shows:

- **Version** of the DSL definition used for this run.
- **Task queue** when executed in Run mode.
- **Parent execution** when this run was invoked from another Process or Transaction.
- **End state** — final status, completion time, and terminal error summary if applicable.

### Step-by-step trace

The trace is a hierarchical timeline of every invocation. The root is always the executed entity. Children are rendered indented beneath their caller.

```
Process LoanDisbursementProcess
├── Helper riskAssessment
├── Function formatCustomerMessage
├── Transaction KYC_CHECK
│   ├── Helper fetchCustomer
│   └── Helper validateDocuments
├── Transaction DEBIT_FUNDING
└── Helper notifySuccess
```

For each step the trace shows:

- Step type icon: Process, Transaction, Helper, or Function.
- Name and, for Transactions, the activity type.
- Status badge at the step level.
- Start timestamp and duration.
- Retry count for the current step.
- Outcome indicator: produced output, threw error, was skipped, or was compensated.

Branches for conditionals, loops, and parallel sections are grouped visually so the control-flow structure is easy to follow.

### Compensation visualization

Compensation steps are shown distinctly from normal forward steps:

- A separate lane, background tint, or dashed connector marks the compensation path.
- Each compensated step displays the original step it rolls back.
- Steps run in reverse Saga order, ending with any Process-level compensation block.
- The badge reads **Compensated** when the whole path finishes, or **Compensation failed** if a rollback step itself errors.

## Tabs

The detail view provides several tabs for deeper inspection.

### Diagram

Renders the Mermaid or BPMN diagram from the Explain report, or reconstructs one from the live execution trace in Run/Preview mode. The diagram is synchronized with the trace: clicking a step scrolls the trace to the matching entry, and vice versa.

- Forward steps are drawn with solid connectors.
- Conditional branches show decision diamonds with labels.
- Parallel sections show forks and joins.
- Compensation paths are drawn with dashed or highlighted connectors and labeled rollback nodes.

### I/O payload

Displays the typed `Context` that flowed through the execution.

- **Input** — the initial payload submitted to the Process/Transaction.
- **Output** — the final payload returned on completion.
- **Per-step I/O** — input context before and output context after each step, expandable per trace row.

Payloads can be viewed as pretty-printed JSON, raw JSON, or a tree. Sensitive fields may be masked according to configured scrubbing rules.

### Metadata

Shows execution-level metadata:

- Correlation ID and request ID.
- DSL definition version and source location.
- Temporal execution metadata in Run mode: workflow ID, run ID, task queue, namespace.
- Environment, tenant, user, and CI context.
- Timeout and retry policy settings resolved for this execution.

### Logs

Aggregates logs emitted by the running Process, Transactions, Helpers, and Functions. Logs are searchable and filterable by:

- Step name.
- Severity.
- Time range.
- Text pattern.

Each log entry carries a timestamp, level, source step, and message.

### Errors

When the execution or any step fails, this tab collects:

- The final error message and stack trace.
- Failure timestamp and failing step.
- Retry history with each attempt's result and backoff duration.
- Link to the compensated steps that ran as a result, if any.
- Suggestions or runbook links when configured.

## Time, duration, and retries

Every row in the trace and every event in the timeline carries precise timing.

- **Timestamps** are displayed in the user's local time zone with ISO-8601 fallback. Hovering reveals the absolute UTC value and epoch millis.
- **Durations** are shown as human-readable intervals (`1.2 s`, `3 m 42 s`, `1 h 5 m`). Hovering reveals the exact millisecond value.
- **Retry counts** appear on the execution header and on each retried step. Expanding a retried step reveals every attempt, its start time, duration, and outcome.

## Navigation and deep links

Every execution detail view has a stable URL based on execution ID and mode, so users can share links, bookmark them, and embed them in alerts or runbooks. From any step users can open:

- The DSL source definition.
- The parent execution.
- Child executions started by the current step.
- Related executions with the same correlation ID.

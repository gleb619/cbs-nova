# Technical Design Document: CBS-Nova Business Orchestration Engine

**Version:** v0.5.1 (consolidated) | **Date:** 2026-04-09 | **Status:** For team review

**Stack:** Java 25 · Spring Boot · Temporal · PostgreSQL · Kotlin Script (.kts) · Gradle 9.x (multi-module) · Vue +
Nuxt.js (admin UI)

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Core Entities](#3-core-entities)
4. [DSL Design (.kts)](#4-dsl-design-kts)
5. [Execution Model](#5-execution-model)
6. [Workflow Lifecycle](#6-workflow-lifecycle)
7. [Mass Operation Model](#7-mass-operation-model)
8. [State Management](#8-state-management)
9. [Build & Deploy Pipeline](#9-build--deploy-pipeline)
10. [API Contract](#10-api-contract)
11. [Versioning Strategy](#11-versioning-strategy)
12. [BPMN Export](#12-bpmn-export)
13. [Admin & Stakeholder UI](#13-admin--stakeholder-ui)
14. [Module Structure](#14-module-structure)
15. [Risks & Open Questions](#15-risks--open-questions)
16. [Out of Scope (v1)](#16-out-of-scope-v1)
17. [References](#17-references)

### Architecture Sub-documents

- [arch/dsl-design.md](arch/dsl-design.md) — Full DSL reference and entity interfaces
- [arch/execution-model.md](arch/execution-model.md) — Execution flow, runEvent vs resumeEvent, context hierarchy
- [arch/workflow-lifecycle.md](arch/workflow-lifecycle.md) — State machine concepts, prolong(), example lifecycle
- [arch/mass-operation.md](arch/mass-operation.md) — Full MassOperation model, lock, signals, retry
- [arch/state-management.md](arch/state-management.md) — Full PostgreSQL schema with indexes
- [arch/build-deploy.md](arch/build-deploy.md) — Gitea strategy, CI/CD steps, dev mode
- [arch/api-contract.md](arch/api-contract.md) — Full request/response shapes for all endpoints
- [arch/versioning.md](arch/versioning.md) — Strict isolation, Temporal workflow ID format
- [arch/bpmn-export.md](arch/bpmn-export.md) — Static and dynamic BPMN, frontend integration
- [arch/module-structure.md](arch/module-structure.md) — Full file tree for all Gradle modules
- [arch/risks.md](arch/risks.md) — Full risk details, resolved decisions, open questions

### UI Sub-documents

- [ui/overview.md](ui/overview.md) — Goals, repo structure, PNPM monorepo layout
- [ui/nuxt-bff.md](ui/nuxt-bff.md) — Nuxt as Backend-for-Frontend: routes, middleware, error shape
- [ui/package-breakdown.md](ui/package-breakdown.md) — admin-core, admin-ui, admin-components, admin-codegen
- [ui/crud-system.md](ui/crud-system.md) — Generic CRUD pages, custom route override
- [ui/filters-datatable.md](ui/filters-datatable.md) — Two-tier filter system, RSQL builder, pagination
- [ui/saved-searches.md](ui/saved-searches.md) — Backend-persisted searches, BFF routes, UI behavior
- [ui/forms-validation.md](ui/forms-validation.md) — Shared form component, Zod validation, field components
- [ui/relation-picker.md](ui/relation-picker.md) — Combobox + modal datatable picker
- [ui/orchestration-ui.md](ui/orchestration-ui.md) — Execution list, detail page, workflow widget
- [ui/settings-ui.md](ui/settings-ui.md) — Settings page, complex dictionaries
- [ui/navigation-abac.md](ui/navigation-abac.md) — Sidebar, ABAC access control, route guard
- [ui/auth-i18n.md](ui/auth-i18n.md) — Keycloak-js auth, i18n setup, translation structure
- [ui/codegen.md](ui/codegen.md) — Code generation trigger, artifacts, OpenAPI extensions
- [ui/dependencies.md](ui/dependencies.md) — Key packages, developer workflow, deferred features

---

## 1. Overview & Goals

### 1.1 Context

The system replaces an existing Spring-bean-based orchestration engine (Event/Transaction/Helper) that suffers from slow
startup, no parallelism, unreliable state persistence, and business rules locked in Java code.

The new system preserves the existing mental model but replaces the runtime with Temporal, PostgreSQL for state, and
Kotlin Script (`.kts`) for business-editable DSL files. Every execution — regardless of whether it uses a full workflow
lifecycle or is a single stateless event — produces a persistent artifact visible to stakeholders and admins.

In v0.5, two capabilities are added on top of the v0.4 base:

- **MassOperation**: orchestrates the same Event or Workflow transition over a large set of data items, driven by various triggers, with per-item tracking, business locking, and signal-based chaining.
- **Refined transitions**: workflow transitions are now full closures — they can run multiple events sequentially or in parallel, resume existing workflow context, and declare custom fault handlers.

### 1.2 Goals

- **Business autonomy**: analysts write and modify DSL files without developer involvement; analysts declare mass operations in DSL without developer involvement
- **Correctness**: every execution instance runs to completion on the DSL version it started with
- **Parallelism**: all steps run as Temporal `CompletablePromise`s; `await()` marks explicit sync points
- **Full observability**: every execution produces a `workflow_execution` artifact with transition log, context, and stakeholder display data; per-item execution tracking with admin report
- **Resilience**: per-item failure isolation — one failed agreement does not stop the batch
- **Lifecycle visibility**: BPMN-based route visualization and funnel/heatmap UI for stakeholders
- **Signal-driven chaining**: mass operations emit signals on partial and full completion, enabling declarative chaining between batches
- **Unified model**: workflowless events are syntactic sugar — a stub workflow with one state

### 1.3 Unified Execution Model

There is no runtime distinction between stateless and workflow mode. Every event execution is backed by a workflow
instance. For events without an explicit `workflow {}` DSL, the framework generates a stub workflow automatically:

```
stub workflow:
  states { COMPLETED }
  initial = "COMPLETED"
  transitions {
    _ -> COMPLETED on Action.SUBMIT runs event(thisEvent)
  }
  terminalStates { COMPLETED }
```

`workflow_execution_id` is never null in `event_execution`. All executions — including individual MassOperation items — appear in admin and stakeholder UI uniformly.

### 1.4 Non-Goals (v1)

- VSCode extension UI (server-side VSCode via `cbs-rules` link covers this in v1)
- Idempotency guarantees on Helper side effects
- Annotation processor for typed Helper/Transaction DSL generation (v2)
- Nexus publishing of typed DSL bindings (v2)
- Stakeholder funnel/heatmap UI frontend (data model ready, v2)
- Temporal UI iframe embed (link only in v1)

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     Client / API Gateway                         │
└────────────────────────────┬─────────────────────────────────────┘
                             │ POST /api/events/execute
                             │ POST /api/mass-operations/trigger
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                   Spring Boot Application                        │
│                                                                  │
│  EventController          MassOperationController                │
│  EventService             MassOperationService                   │
│  WorkflowResolver         MassOperationScheduler (cron/trigger)  │
│  WorkflowExecutor         MassOperationExecutor                  │
│  ContextEvaluator         SignalEmitter                          │
│  ContextEncryptionService                                        │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                 DSL Runtime Module                     │     │
│  │  WorkflowDefinition / EventDefinition /                │     │
│  │  TransactionDefinition / HelperDefinition /            │     │
│  │  MassOperationDefinition / SignalDefinition            │     │
│  └────────────────────────────────────────────────────────┘     │
└──────────┬────────────────────────────────────┬─────────────────┘
           │                                    │
           ▼                                    ▼
┌──────────────────────┐         ┌──────────────────────────────────┐
│   Temporal Server    │         │           PostgreSQL              │
│                      │         │                                  │
│  EventWorkflow       │         │  workflow_execution  (encrypted)  │
│  TransactionActivity │         │  event_execution     (encrypted)  │
│  MassOpWorkflow      │         │  workflow_transition_log         │
│  MassOpItemActivity  │         │  mass_operation_execution        │
│                      │         │  mass_operation_item             │
└──────────────────────┘         └──────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│              Vue + Nuxt.js Admin Panel               │
│  ├─ Execution detail + BPMN viewer                   │
│  ├─ MassOperation report (counts, per-item drill)    │
│  ├─ Failed item re-run button                        │
│  ├─ Temporal UI link                                 │
│  └─ cbs-rules VSCode server link                     │
└──────────────────────────────────────────────────────┘

┌──────────────┐   ┌──────────────────┐   ┌────────────────────┐
│ Gitea        │   │ GitLab CI/Jenkins│   │ MQ / Webhook       │
│ cbs-rules    │   │ compile → Docker │   │ external triggers  │
└──────────────┘   └──────────────────┘   └────────────────────┘
```

Temporal holds **only PKs** (`event_execution.id`, `workflow_execution.id`, `mass_operation_execution.id`,
`mass_operation_item.id`). All real state lives in PostgreSQL. The Spring layer evaluates `context {}` blocks before
touching Temporal — bad input is rejected before any Temporal invocation, avoiding orphaned workflow instances.

→ Detailed component breakdown: [arch/execution-model.md](arch/execution-model.md)

---

## 3. Core Entities

| Entity            | Description                                                                                                                                                                                                                                                            |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Workflow**      | Top-level DSL entity defining a state machine. Declares `states`, `initial`, `terminalStates`, and `transitions`. All fields are optional — the framework infers them from transition declarations when omitted.                                                       |
| **Event**         | Triggered operation that lives inside a workflow state. Declares `context {}`, `display {}`, `transactions {}`, and `finish {}` blocks. May be referenced by workflow transitions or called standalone via a stub workflow.                                            |
| **Transaction**   | Temporal Activity unit of work. Declares optional `preview()`, `execute()`, and `rollback()` closures. In banking, `rollback` is a compensating entry — a real counter-entry against the ledger, not a technical undo.                                                 |
| **Helper**        | Spring bean or inline DSL (SQL/HTTP). Always typed as `HelperFunction<I extends HelperInput, O extends HelperOutput>`. Used inside `context {}` and `transactions {}` blocks for external calls.                                                                       |
| **MassOperation** | Top-level DSL entity for batch orchestration. Defines a data source, one or more triggers, a business lock, per-item execution logic, and signals to emit on partial and full completion. Each item is processed independently; failures are isolated and re-runnable. |
| **Condition**     | Reusable Boolean DSL block. Declared in `.kts` files and referenced by name inside `when/then/otherwise` expressions within `transactions {}`.                                                                                                                         |

→ Detailed interfaces: [arch/dsl-design.md](arch/dsl-design.md)

---

## 4. DSL Design (.kts)

### File & Folder Convention

DSL files are stored in the `cbs-rules` Gitea repository. The repository also contains a minimal
`build.gradle.kts` used for local validation and development tooling entrypoints. Each event owns a folder. All DSL
objects for that event — event definition, transactions, helpers — live in the same folder, analogous to a Java
package. Workflow definitions live at the repository root. Mass operation definitions live under `mass-operations/`.

```
cbs-rules/
├── global/
│   └── banking-helpers.helper.kts          ← available to all events
│
├── loan-disbursement/
│   ├── loan-disbursement.event.kts
│   ├── debit-funding-account.transaction.kts
│   ├── credit-borrower-account.transaction.kts
│   ├── post-disbursement-entry.transaction.kts
│   └── loan-helpers.helper.kts             ← scoped to this event only
│
├── loan-onboarding/
│   ├── loan-onboarding.event.kts
│   ├── kyc-check.transaction.kts
│   ├── credit-scoring.transaction.kts
│   └── ...
│
├── loan-contract.workflow.kts              ← workflow at root, references event folders
│
└── mass-operations/
    ├── interest-charge/
    │   ├── interest-charge.mass.kts
    │   └── interest-charge-helpers.helper.kts
    ├── penalty-accrual/
    │   └── penalty-accrual.mass.kts
    └── government-upload/
        └── government-upload.mass.kts
```

Files use these extensions: `.event.kts`, `.transaction.kts`, `.helper.kts`, `.workflow.kts`, `.mass.kts`. The
`compileDsl` Gradle task resolves all `#import` declarations and performs semantic validation — missing references,
undeclared states, and unresolvable helpers all fail the build.

→ Full DSL reference: [arch/dsl-design.md](arch/dsl-design.md)

---

## 5. Execution Model

### Action Enum

```java
public enum Action {
    PREVIEW, SUBMIT, APPROVE, REJECT, CANCEL, CLOSE, ROLLBACK
}
```

| Action     | Meaning                                                           |
|------------|-------------------------------------------------------------------|
| `PREVIEW`  | Dry-run context evaluation only — no Temporal, no state change    |
| `SUBMIT`   | First submission; creates workflow instance                       |
| `APPROVE`  | Advances workflow from a pending state                            |
| `REJECT`   | Sends workflow back (often stays in same state with notification) |
| `CANCEL`   | Terminates to CANCELLED terminal state                            |
| `CLOSE`    | Completes to CLOSED terminal state                                |
| `ROLLBACK` | Manual recovery from FAULTED state                                |

### Signal Types

```java
public enum SignalType {
    PARTIAL,    // emitted after processing each configured batch of items
    COMPLETED   // emitted when all items are processed
}

public record Signal(
    String     source,      // mass operation code emitting this signal, or "EXTERNAL"
    SignalType type,
    Map<String, Object> payload
) {
    public static Signal external(String name) { ... }
    public static Signal from(String massOpCode, SignalType type) { ... }
}
```

| Signal      | When emitted                                               | Payload                                            |
|-------------|------------------------------------------------------------|----------------------------------------------------|
| `PARTIAL`   | After every N items processed (N configured per operation) | `processedSoFar`, `failedSoFar`, any custom fields |
| `COMPLETED` | After all items processed (including failures)             | `totalProcessed`, `totalFailed`, any custom fields |

Signals are received by other mass operations via `onSignal(Signal.from(...))` in their `triggers {}` block, or consumed
by external systems via MQ/webhook subscription.

→ Full execution details: [arch/execution-model.md](arch/execution-model.md)

---

## 6. Workflow Lifecycle

A workflow is a state machine. Transitions declare `from` state, `to` state, triggering `Action`, the `Event` to run,
and an optional `onFault` target (defaults to `"FAULTED"`).

Key state machine concepts:

| Concept          | Description                                                                                  |
|------------------|----------------------------------------------------------------------------------------------|
| `states`         | Optional. Inferred from transitions if omitted.                                              |
| `initial`        | Optional. Defaults to first `from` state in transitions.                                     |
| `terminalStates` | Optional. Defaults to states with no outgoing transitions.                                   |
| `FAULTED`        | Framework-reserved. Set on auto-rollback after transaction failure.                          |
| `onFault`        | Per-transition closure. Target state on failure. Defaults to `"FAULTED"`.                    |
| Stub workflow    | Auto-generated for events with no explicit workflow DSL. One state: COMPLETED.               |
| Context sharing  | `workflow_execution.context` is shared across all transitions. No fresh seed between states. |

When `ctx.prolong(action)` is called inside `finish {}`, the framework triggers the next workflow transition
automatically — without a network round-trip. This is the integration point for legacy behavior where certain state
progressions were automatic. There is no maximum chain depth; DSL authors are responsible for avoiding infinite prolong
loops. Terminal states are always checked — `prolong()` on a terminal state is a no-op.

Transitions in v0.5 are full closures. A single transition can call multiple events via `ctx.runEvent()` (async by
default, parallel `CompletablePromise`) or `ctx.resumeEvent()` (loads saved context from PostgreSQL, skips `context {}`
and `transactions {}`, re-runs only `finish {}` and `display {}`). `ctx.await(...)` provides explicit synchronization
barriers.

→ Details: [arch/workflow-lifecycle.md](arch/workflow-lifecycle.md)

---

## 7. Mass Operation Model

A MassOperation defines a batch execution over a data source. Each item in the source is processed independently —
failure of one item does not stop the operation. Failed items are logged and re-runnable from the admin UI.

### Trigger Types

| Trigger type   | DSL                                                  | Description                                   |
|----------------|------------------------------------------------------|-----------------------------------------------|
| Cron           | `cron("0 1 * * *")`                                  | Standard cron expression                      |
| Exact time     | `once(at = "2025-12-31T23:59:00")`                   | Run once at specific datetime                 |
| Periodic       | `every(days = 1)`, `every(weeks = 1)`                | Repeating interval                            |
| External       | `onSignal(Signal.external("NAME"))`                  | MQ message or webhook                         |
| Signal from op | `onSignal(Signal.from("OP_CODE", Signal.COMPLETED))` | Triggered by another mass op completing       |
| Signal partial | `onSignal(Signal.from("OP_CODE", Signal.PARTIAL))`   | Triggered on partial completion of another op |

Multiple triggers can be declared — any one firing starts the operation, subject to the lock closure returning `true`.
The business lock closure is evaluated at run start; `false` aborts and logs `LOCKED` status.

→ Full model: [arch/mass-operation.md](arch/mass-operation.md)

---

## 8. State Management

### workflow_execution

| Column          | Type         | Notes                                     |
|-----------------|--------------|-------------------------------------------|
| `id`            | BIGSERIAL PK | Internal; exposed as `eventNumber` in API |
| `workflow_code` | VARCHAR(100) |                                           |
| `dsl_version`   | VARCHAR(50)  | Locked at instance creation               |
| `current_state` | VARCHAR(100) |                                           |
| `status`        | VARCHAR(20)  | ACTIVE / CLOSED / FAULTED                 |
| `context`       | JSONB        | Encrypted at application level            |
| `display_data`  | JSONB        | Encrypted at application level            |
| `performed_by`  | VARCHAR(200) | User from JWT/session                     |
| `created_at`    | TIMESTAMPTZ  |                                           |
| `updated_at`    | TIMESTAMPTZ  |                                           |

### mass_operation_execution

| Column                 | Type         | Notes                                                        |
|------------------------|--------------|--------------------------------------------------------------|
| `id`                   | BIGSERIAL PK |                                                              |
| `code`                 | VARCHAR(100) | Mass operation DSL code                                      |
| `category`             | VARCHAR(100) | e.g. CREDITS, DEPOSITS                                       |
| `dsl_version`          | VARCHAR(50)  | Locked at run start                                          |
| `status`               | VARCHAR(30)  | RUNNING / DONE / DONE_WITH_FAILURES / LOCKED / FAULTED       |
| `context`              | JSONB        | Encrypted; shared for all items                              |
| `total_items`          | BIGINT       | Set after source evaluation                                  |
| `processed_count`      | BIGINT       | Incremented as items complete                                |
| `failed_count`         | BIGINT       | Incremented per failed item; decremented on successful retry |
| `trigger_type`         | VARCHAR(50)  | CRON / ONCE / SIGNAL_EXTERNAL / SIGNAL_FROM_OP               |
| `trigger_source`       | VARCHAR(200) | Signal source or cron expression                             |
| `performed_by`         | VARCHAR(200) | User or system/scheduler                                     |
| `started_at`           | TIMESTAMPTZ  |                                                              |
| `completed_at`         | TIMESTAMPTZ  |                                                              |
| `temporal_workflow_id` | VARCHAR(200) |                                                              |

A companion `mass_operation_item` table holds one row per item per run, with `item_key`, `item_data` (encrypted),
`status` (PENDING / RUNNING / DONE / FAILED), `error_message`, and a `retry_of` foreign key for retried items.

→ Full schema: [arch/state-management.md](arch/state-management.md)

---

## 9. Build & Deploy Pipeline

The `cbs-rules` Gitea repository stores DSL sources (`.kts`) plus a minimal `build.gradle.kts` for validation/dev
execution. It does not store application runtime configuration. The `main` branch is production; feature branches are
used for new or changed rules. All changes flow through CI before deployment.

```
cbs-rules Gitea: push to branch
  │
  └─► GitLab CI / Jenkins
        │
        ├─ backend: downloadDsl (JGit)
        │    └─ Clone/pull DSL branch from cbs-rules (fallback to main)
        │
        ├─ dsl/dsl-compiler: compileDsl
        │    ├─ Compile all .kts files
        │    ├─ Resolve all #import declarations
        │    ├─ Semantic validation:
        │    │    ├─ All referenced events exist in registry
        │    │    ├─ All referenced helpers exist (code or inline)
        │    │    ├─ All transition target states declared in workflow states
        │    │    ├─ All condition references resolve
        │    │    └─ All transaction references resolve to known beans or DSL objects
        │    └─ Produce: dsl-rules-{version}.jar
        │
        ├─ dsl/dsl-compiler: publishDslToMavenLocal
        │    └─ Publish dsl-rules-{version}.jar to local Maven cache
        │
        ├─ backend: buildApp
        │    └─ Resolve dsl-rules artifact as runtimeOnly dependency
        │
        └─ Docker build → push → deploy
```

Semantic validation in v0.5 is extended to cover mass operation DSL: all events referenced in `item { ctx -> }` must
exist in the registry; all workflows referenced in `ctx.runWorkflow()` must exist; all helpers used in `source {}`,
`lock {}`, and `context {}` must resolve; `Signal.from("OP_CODE", ...)` must reference a known mass operation code.

A dev-mode endpoint (`POST /dev/dsl/execute`, `@Profile("dev")`) provides lenient execution via `dsl/dsl-runtime`:
it uses JGit to sync DSL sources from Gitea and evaluates `.kts` without compile/package for fast feedback. This mode
is development-only. Production runtime must use compiled DSL classes/JAR. Temporal is still required and running;
state is persisted normally.

→ Details: [arch/build-deploy.md](arch/build-deploy.md)

---

## 10. API Contract

All endpoints require `Authorization: Bearer {token}`. `userId` is resolved from JWT/session. `eventNumber` maps to
`workflow_execution.id` internally — `workflowInstanceId` is never exposed in the API.

| Method | Path                                                      | Description                                                                                                      |
|--------|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `POST` | `/api/events/execute`                                     | Execute a single event or advance a workflow transition. Body: `{ code, action, eventNumber?, eventParameters }` |
| `POST` | `/api/mass-operations/trigger`                            | Manually trigger a mass operation. Body: `{ code, context? }`                                                    |
| `GET`  | `/api/mass-operations/{executionId}`                      | Get mass operation execution status with processed/failed counts                                                 |
| `GET`  | `/api/mass-operations/{executionId}/items`                | List items for a mass operation run; supports `?status=FAILED&page=0&size=50`                                    |
| `POST` | `/api/mass-operations/{executionId}/items/{itemId}/retry` | Retry a single failed item; creates new `mass_operation_item` row linked to same execution                       |
| `GET`  | `/api/workflows/{code}/bpmn`                              | Export static BPMN XML for a workflow definition                                                                 |
| `GET`  | `/api/workflows/{code}/bpmn/{eventNumber}`                | Export dynamic BPMN XML for a specific workflow instance                                                         |
| `GET`  | `/api/workflows/{code}/bpmn/aggregate`                    | Export heatmap BPMN XML across all instances of a workflow                                                       |
| `POST` | `/dev/dsl/execute`                                        | (`@Profile("dev")`) Lenient mode: JGit sync + direct `.kts` execution without compile/package                    |

Fault responses use structured error codes: `INVALID_TRANSITION`, `MISSING_PARAMETERS`, `CONTEXT_FAULT`. Mass operation lock rejection returns `LOCKED` status in the response body.

→ Full request/response shapes: [arch/api-contract.md](arch/api-contract.md)

---

## 11. Versioning Strategy

**Strict isolation. No interop.**

Every execution runs to completion on the DSL version it started with. `workflow_execution.dsl_version` is the
authority. New instances always use the latest compiled version. Context is shared across transitions of the same
instance regardless of version upgrades — the version locked at instance creation applies throughout.

Mass operations follow the same rule: a running mass operation continues with the DSL version it started with even if a
new version deploys mid-run.

Version format: `{semver}-{gitCommitShort}` — e.g. `1.5.0-a3f91bc`. Embedded in the compiled JAR manifest.

Temporal workflow ID format: `{eventCode}-{eventNumber}-{dslVersion}`. Temporal `Workflow.getVersion()` guards against
structural changes. Old workers drain in-flight workflows; new workers serve new starts.

→ Details: [arch/versioning.md](arch/versioning.md)

---

## 12. BPMN Export

### Purpose

Workflow definitions and execution instances can be exported as BPMN 2.0 XML and visualized via `bpmn-js` in the admin
panel. This gives stakeholders a familiar process map rather than a raw state machine diagram, mirroring the
Camunda/Flowable instance detail view.

### Two Export Modes

**Static (workflow template):** Generated from the Workflow DSL definition. Shows the complete state machine as a BPMN
diagram — all states as tasks/gateways, all transitions as sequence flows. No execution data. Used for documentation and
process review.

**Dynamic (execution instance heatmap):** Generated from a specific `workflow_execution` + `workflow_transition_log`.
Overlays the static diagram with the current state (highlighted node), the route taken (visited transitions
highlighted), a heatmap overlay of transition frequency across all instances, and FAULTED states marked with error
indicators. Dynamic heatmap is deferred to v2; static template ships in v1.

States map to BPMN `<userTask>` or `<serviceTask>` elements. Transitions map to `<sequenceFlow>` with conditions.
Terminal states map to `<endEvent>`. FAULTED state maps to `<boundaryErrorEvent>`.

→ Details: [arch/bpmn-export.md](arch/bpmn-export.md)

---

## 13. Admin & Stakeholder UI

### Overview

- **Temporal UI integration**: a "View in Temporal" button on each execution detail page links to
  `{temporal-ui-host}/namespaces/default/workflows/{temporal_workflow_id}`. `temporal_workflow_id` is stored in
  `event_execution`. v2 will embed via `<iframe>`.
- **DSL editing via server-side VSCode**: an "Edit DSL Rules" button in the admin panel header opens `cbs-rules` in a
  code-server instance with `.kts` language support. Analysts and developers get a browser-based IDE with autocomplete
  driven by compiled `dsl-runtime` types.
- **Stakeholder display data**: the `display {}` block populates `workflow_execution.display_data` JSONB (encrypted).
  The admin UI shows business-meaningful labels alongside the state machine visualization. If `display {}` is omitted,
  the full `context {}` output is used as display data by default. `display {}` can also appear inside individual
  transaction blocks.
- **Mass operation report UI**: a dedicated view per `mass_operation_execution` shows a summary card (code, category,
  trigger type, DSL version, start/end time, total/processed/failed with progress bar), a filterable item list (ALL /
  DONE / FAILED / RUNNING / PENDING), per-item error messages with retry buttons, and drill-down to the corresponding
  `workflow_execution` detail for each item.
- **Funnel / Heatmap UI**: deferred to v2. Data model and schema are ready in v1.

### Admin UI Sub-documents

- [ui/overview.md](ui/overview.md) — Goals, repo structure, PNPM monorepo layout
- [ui/nuxt-bff.md](ui/nuxt-bff.md) — Nuxt as Backend-for-Frontend: routes, middleware, error shape
- [ui/package-breakdown.md](ui/package-breakdown.md) — admin-core, admin-ui, admin-components, admin-codegen
- [ui/crud-system.md](ui/crud-system.md) — Generic CRUD pages, custom route override
- [ui/filters-datatable.md](ui/filters-datatable.md) — Two-tier filter system, RSQL builder, pagination
- [ui/saved-searches.md](ui/saved-searches.md) — Backend-persisted searches, BFF routes, UI behavior
- [ui/forms-validation.md](ui/forms-validation.md) — Shared form component, Zod validation, field components
- [ui/relation-picker.md](ui/relation-picker.md) — Combobox + modal datatable picker
- [ui/orchestration-ui.md](ui/orchestration-ui.md) — Execution list, detail page, workflow widget
- [ui/settings-ui.md](ui/settings-ui.md) — Settings page, complex dictionaries
- [ui/navigation-abac.md](ui/navigation-abac.md) — Sidebar, ABAC access control, route guard
- [ui/auth-i18n.md](ui/auth-i18n.md) — Keycloak-js auth, i18n setup, translation structure
- [ui/codegen.md](ui/codegen.md) — Code generation trigger, artifacts, OpenAPI extensions
- [ui/dependencies.md](ui/dependencies.md) — Key packages, developer workflow, deferred features

---

## 14. Module Structure

| Module                | Description                                                                                                                                                         |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app`                 | Spring Boot entry point (Java 25). Controllers, services, repositories, Temporal client wiring.                                                                     |
| `dsl/dsl-api`         | Shared DSL contracts and context interfaces used by both compiled runtime and lenient dev execution paths.                                                          |
| `dsl/dsl-compiler`    | Compiler/validator module: `.kts` → Java classes/JAR. Provides `compileDsl` and publish-to-local tasks used by backend build/runtime pipeline.                      |
| `dsl/dsl-runtime`     | Runtime integration module. In production it loads compiled DSL artifacts; in development it provides lenient non-compiled `.kts` execution path for fast feedback. |
| `temporal-core`       | Temporal workflow and activity base classes (Java). `EventWorkflow`, `TransactionActivity`, `MassOpWorkflow`, `MassOpItemActivity`.                                 |
| `bpmn-export`         | Static BPMN XML generation from DSL model (`BpmnExporter`, `StaticBpmnGenerator`, `DynamicBpmnGenerator`).                                                          |
| `mass-operation-core` | MassOperation Temporal workflow + activity + scheduler. Extracted as a separate module in v0.5.                                                                     |

→ Full file tree: [arch/module-structure.md](arch/module-structure.md)

---

## 15. Risks & Open Questions

| Risk                                                                          | Severity |
|-------------------------------------------------------------------------------|----------|
| Temporal worker versioning — in-flight isolation                              | High     |
| Helper idempotency — Temporal retries may double side effects                 | High     |
| Compensating transactions — wrong rollback() breaks accounting ledger         | High     |
| Partial compensation failure — rollback itself fails mid-way                  | High     |
| Mass op concurrency — 80k items in parallel may overwhelm downstream services | High     |
| JSONB encryption performance — encrypt/decrypt on every read/write            | Medium   |
| prolong() loop risk — infinite prolong chain with no terminal state           | Medium   |
| Mass op lock race condition — two scheduler instances fire simultaneously     | Medium   |
| Signal delivery guarantee — PARTIAL/COMPLETED signals must not be lost        | Medium   |
| resumeEvent misuse — calling resumeEvent when no prior execution exists       | Medium   |

→ Full risk details and open questions: [arch/risks.md](arch/risks.md)

---

## 16. Out of Scope (v1)

- Annotation processor for typed Helper/Transaction DSL generation (v2)
- Nexus publishing of typed DSL bindings (v2)
- Stakeholder funnel/heatmap UI frontend (v2)
- Temporal UI iframe embed (v2)
- Mass operation item list export to Excel/PDF (v2)
- On-the-fly source re-computation for failed items (v2)
- `DRY_RUN`, `COMPENSATE`, `SUSPEND`, `REACTIVATE` actions
- Dynamic BPMN instance heatmap (v2)

---

## 17. References

### Temporal

- [Temporal Java SDK — Workflow Versioning](https://docs.temporal.io/dev-guide/java/versioning)
- [Temporal Web UI — Self-Hosted Deployment](https://docs.temporal.io/web-ui)
- [Temporal Workflow Determinism & Versioning — Temporal Blog](https://temporal.io/blog/workflow-versioning)

### Community

- [Как мы строили оркестрацию бизнес-процессов на Temporal (Habr)](https://habr.com/ru/articles/970730/)
- [Temporal: практика применения в продакшене (Habr)](https://habr.com/ru/articles/966972/)

### Architecture & Patterns

- [Saga Pattern & Compensating Transactions — Microsoft Architecture Guide](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Kotlin DSL Design Patterns — JetBrains Blog](https://blog.jetbrains.com/kotlin/2021/10/kotlin-dsl-best-practices/)
- [bpmn-js — BPMN 2.0 Rendering Library](https://bpmn.io/toolkit/bpmn-js/)

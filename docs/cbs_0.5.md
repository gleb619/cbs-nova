# Technical Design Document: Business Orchestration Engine

**Version:** 0.5-draft  
**Status:** For team discussion  
**Stack:** Java 25, Spring Boot, Temporal, PostgreSQL, Kotlin Script (.kts), Gradle 9.x (multi-module), Vue + Nuxt.js (admin)  
**Date:** 2025

**Changelog v0.5:**
- New top-level DSL entity: `MassOperation` — batch orchestration over large data sets
- MassOperation triggers: cron, exact time, once, periodic, external (MQ/webhook), signal from another MassOperation
- MassOperation signals: partial-completion and full-completion signals for chaining
- Business lock via DSL closure + `mass_operation_item` table for item-level tracking
- Per-item failure logging; failed items re-runnable individually from admin UI
- MassOperation report UI: processed/failed counts, per-item drill-down
- Transition closure refactored: `ctx.runEvent()` / `ctx.resumeEvent()`, configurable async
- `onFault {}` closure on transitions with `ctx.setStatus()`
- Transitions receive `ctx ->` and can call multiple events per transition

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Core Entities](#3-core-entities)
4. [DSL Design (.kts)](#4-dsl-design-kts)
5. [Execution Model](#5-execution-model)
6. [Workflow Lifecycle Model](#6-workflow-lifecycle-model)
7. [Mass Operation Model](#7-mass-operation-model)
8. [State Management](#8-state-management)
9. [Build & Deploy Pipeline](#9-build--deploy-pipeline)
10. [API Contract](#10-api-contract)
11. [Versioning Strategy](#11-versioning-strategy)
12. [Admin & Stakeholder UI](#12-admin--stakeholder-ui)
13. [BPMN Export](#13-bpmn-export)
14. [Module Structure](#14-module-structure)
15. [Risks & Open Questions](#15-risks--open-questions)
16. [Out of Scope (v1)](#16-out-of-scope-v1)
17. [References](#17-references)

---

## 1. Overview & Goals

### 1.1 Context

The system replaces an existing Spring-bean-based orchestration engine. It preserves the mental model (Event / Transaction / Helper / Workflow) and adds two new capabilities in v0.5:

- **MassOperation**: orchestrates the same Event or Workflow transition over a large set of data items, driven by various triggers, with per-item tracking, business locking, and signal-based chaining.
- **Refined transitions**: workflow transitions are now full closures — they can run multiple events sequentially or in parallel, resume existing workflow context, and declare custom fault handlers.

### 1.2 Goals

All goals from v0.4 remain. Added:

- **Batch autonomy**: analysts declare mass operations in DSL without developer involvement
- **Resilience**: per-item failure isolation — one failed agreement does not stop the batch
- **Observability**: per-item execution tracking, admin report with processed/failed breakdown
- **Signal-driven chaining**: mass operations emit signals on partial and full completion, enabling declarative chaining between batches

### 1.3 Unified Execution Model

Unchanged from v0.4. Every execution (single event, workflow transition, or mass operation item) is backed by a `workflow_execution` instance. `workflow_execution_id` is never null.

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

---

## 3. Core Entities

### 3.1 MassOperation (NEW)

Top-level DSL entity. Defines a batch operation over a data source, driven by one or more triggers. Each item in the data source is processed independently — failures are isolated, logged, and re-runnable.

```
MassOperation
├── code: String
├── category: String                    // e.g. "CREDITS", "DEPOSITS", "COLLECTIONS"
├── source: SourceDefinition            // how to load the data set
├── trigger: List<TriggerDefinition>    // when to run
├── lock: LockDefinition                // business lock DSL closure
├── context: ContextBlock               // shared context for all items (e.g. run date)
├── item: ItemDefinition                // what to do per item
└── signals: SignalDefinition           // what signals to emit and when
```

### 3.2 Workflow

Unchanged from v0.4, with transition closure refactor (see section 4.3).

### 3.3 Transaction, Helper, Condition, Event

Unchanged from v0.4.

---

## 4. DSL Design (.kts)

### 4.1 File & Folder Convention

Mass operation DSL files live in their own folders under `cbs-rules/mass-operations/`:

```
cbs-rules/
├── global/
│   └── banking-helpers.helper.kts
│
├── loan-disbursement/
│   └── ...
│
├── loan-contract.workflow.kts
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

### 4.2 Import System

Unchanged from v0.4. Mass operation DSL files use the same `#import` syntax:

```kotlin
#import mass-operations.interest-charge.* as ic
#import loan-disbursement.* as disb
#import global.banking-helpers
#import framework.Action
#import framework.Signal
```

### 4.3 Workflow DSL — Refined Transitions

Transitions are now full closures receiving `ctx`. Each transition can run multiple events — async by default (parallel `CompletablePromise`), or explicitly awaited. `ctx.resumeEvent()` loads the existing workflow context from PG and re-runs `finish {}` and `display {}` without recalculating `context {}` or `transactions {}` — it adds `ctx.isResumed = true` so DSL authors can branch on it.

`onFault {}` is a closure per transition, replacing the simple `onFault "FAULTED"` string.

```kotlin
// loan-contract.workflow.kts
#import loan-onboarding.* as onb
#import loan-disbursement.* as disb
#import loan-cancellation.* as canc
#import loan-closure.* as clos
#import global.banking-helpers
#import framework.Action

workflow("LOAN_CONTRACT") {

    states     { DRAFT, ENTERED, ACTIVE, CANCELLED, CLOSED, FAULTED }
    initial    = "ENTERED"
    terminal   { CLOSED, CANCELLED }

    transitions { ctx ->

        DRAFT -> ENTERED on Action.SUBMIT {
            // Multiple events per transition — async by default
            val agreement  = ctx.runEvent(onb.LOAN_CREATE_AGREEMENT)
            val notify     = ctx.runEvent("LOAN_ONBOARDING_NOTIFICATION")
            ctx.await(agreement, notify)     // explicit barrier if needed
        } onFault { ctx ->
            ctx.setStatus(states.FAULTED)
            ctx.runEvent("LOAN_FAULT_NOTIFICATION")
        }

        ENTERED -> ACTIVE on Action.APPROVE {
            // resumeEvent: loads saved context from PG, re-runs finish/display only.
            // ctx.isResumed = true inside the event DSL for branching.
            ctx.resumeEvent(disb.LOAN_DISBURSEMENT)
        } onFault { ctx ->
            ctx.setStatus(states.FAULTED)
        }

        ENTERED -> CANCELLED on Action.CANCEL {
            ctx.runEvent(canc.LOAN_CANCELLATION)
        } onFault { ctx ->
            ctx.setStatus(states.FAULTED)
        }

        ENTERED -> ENTERED on Action.REJECT {
            ctx.runEvent("LOAN_REJECTION_NOTICE")
        }

        ACTIVE -> CLOSED on Action.CLOSE {
            ctx.runEvent(clos.LOAN_CLOSURE)
        } onFault { ctx ->
            ctx.setStatus(states.FAULTED)
        }

        ACTIVE -> CANCELLED on Action.CANCEL {
            ctx.runEvent(canc.LOAN_EARLY_TERMINATION)
        } onFault { ctx ->
            ctx.setStatus(states.FAULTED)
        }

        FAULTED -> ENTERED on Action.ROLLBACK {
            ctx.runEvent("LOAN_FAULT_COMPENSATION")
        }
    }
}
```

**`runEvent` vs `resumeEvent`:**

| Method | context {} | transactions {} | finish {} | display {} | ctx.isResumed |
|---|---|---|---|---|---|
| `ctx.runEvent(event)` | recalculated | executed | executed | executed | false |
| `ctx.resumeEvent(event)` | loaded from PG | skipped | executed | executed | true |

### 4.4 MassOperation DSL

```kotlin
// mass-operations/interest-charge/interest-charge.mass.kts
#import mass-operations.interest-charge.* as ic
#import global.banking-helpers
#import framework.Action
#import framework.Signal

massOperation("INTEREST_CHARGE") {

    category = "CREDITS"

    // --- Triggers (one or more, at least one required) ---
    triggers {
        // Run every day at 01:00
        cron("0 1 * * *")

        // Run once at a specific moment
        once(at = "2025-12-31T23:59:00")

        // Run on external signal from MQ or webhook
        onSignal(Signal.external("INTEREST_CHARGE_TRIGGER"))

        // Run when another mass operation emits its COMPLETED signal
        onSignal(Signal.from("PENALTY_ACCRUAL", Signal.COMPLETED))

        // Run when another mass operation emits its PARTIAL signal
        // (e.g. start uploading as soon as first batch of penalties is ready)
        onSignal(Signal.from("PENALTY_ACCRUAL", Signal.PARTIAL))
    }

    // --- Shared context for all items (evaluated once before processing starts) ---
    // Use this for values that must be fixed at run start (e.g. business date).
    context { ctx ->
        ctx["businessDate"]  = ctx["date"] ?: ctx.helper("CURRENT_BUSINESS_DATE", mapOf())
        ctx["interestRates"] = ctx.helper("LOAD_INTEREST_RATE_TABLE", mapOf(
            "date" to ctx["businessDate"]
        ))
    }

    // --- Data source: returns a collection of items to process ---
    source { ctx ->
        ctx.helper("SQL_CLIENT", mapOf(
            "QUERY"  to """
                SELECT agreement_id, customer_id, outstanding_balance, currency
                FROM credit_agreements
                WHERE status = 'ACTIVE' AND next_interest_date <= :businessDate
            """,
            "PARAMS" to mapOf("businessDate" to ctx["businessDate"])
        )) as List<Map<String, Any>>
    }

    // --- Business lock: prevents concurrent runs ---
    // The framework saves item IDs to mass_operation_item on start.
    // This closure defines whether a new run is allowed.
    lock { ctx ->
        val running = ctx.helper("SQL_CLIENT", mapOf(
            "QUERY"  to """
                SELECT COUNT(*) FROM mass_operation_execution
                WHERE code = 'INTEREST_CHARGE'
                  AND status = 'RUNNING'
                  AND started_at > NOW() - INTERVAL '24 hours'
            """,
            "PARAMS" to mapOf()
        )) as Long
        running == 0L   // true = allowed to start, false = locked
    }

    // --- Per-item execution ---
    // Each item from source is processed independently.
    // Failure of one item does not stop the operation.
    item { ctx ->

        // ctx["item"] contains the current source row
        val agreementId = ctx["item"]["agreement_id"] as String

        // Decide: run a workflow transition or a pure event
        when { ctx["item"]["has_workflow"] == true } then {
            // Advance existing workflow instance for this agreement
            ctx.runWorkflow(
                code        = "LOAN_CONTRACT",
                action      = Action.APPROVE,
                eventNumber = ctx["item"]["event_number"] as Long,
                params      = mapOf(
                    "agreementId"  to agreementId,
                    "businessDate" to ctx["businessDate"],
                    "rate"         to ctx["interestRates"][ctx["item"]["currency"]]
                )
            )
        } otherwise {
            // Run a pure event for this item
            ctx.runEvent("INTEREST_CHARGE_EVENT", mapOf(
                "agreementId"  to agreementId,
                "businessDate" to ctx["businessDate"],
                "rate"         to ctx["interestRates"][ctx["item"]["currency"]]
            ))
        }
    }

    // --- Signals emitted during execution ---
    signals {
        // Emitted after each configured batch size is processed
        partial(every = 1000) { ctx ->
            ctx["processedSoFar"] = ctx.processedCount
            ctx["failedSoFar"]    = ctx.failedCount
        }

        // Emitted when all items are processed (including failures)
        completed { ctx ->
            ctx["totalProcessed"] = ctx.processedCount
            ctx["totalFailed"]    = ctx.failedCount
            ctx["businessDate"]   = ctx["businessDate"]
        }
    }

    // --- Post-execution hook (runs after all items, success or failure) ---
    finish { ctx, ex ->
        if (ctx.failedCount > 0) {
            ctx.helper("SEND_BATCH_FAILURE_REPORT", mapOf(
                "operation"  to "INTEREST_CHARGE",
                "failed"     to ctx.failedCount,
                "total"      to ctx.processedCount,
                "date"       to ctx["businessDate"]
            ))
        }
    }
}
```

### 4.5 Event DSL — Unchanged

See v0.4 section 4.4. No changes. Events remain unaware of whether they are called from a single execution or a mass operation. The only addition: inside an event called from a mass operation, `ctx.isMassOperation` is `true` and `ctx["item"]` contains the source row.

### 4.6 MassOperation Item Context

When a mass operation calls an event or workflow per item, the following context variables are automatically injected:

| Variable | Description |
|---|---|
| `ctx["item"]` | The source row for this item (Map from data source) |
| `ctx.isMassOperation` | `true` when called from a mass operation |
| `ctx["massOperationCode"]` | Code of the parent mass operation |
| `ctx["businessDate"]` | From mass operation context block |
| `ctx.isResumed` | `true` when called via `ctx.resumeEvent()` |

These are available in event/workflow DSL via `when/then/otherwise` for mass-operation-specific branching.

---

## 5. Execution Model

### 5.1 Action Enum

Unchanged from v0.4:

```java
public enum Action {
    PREVIEW, SUBMIT, APPROVE, REJECT, CANCEL, CLOSE, ROLLBACK
}
```

### 5.2 Signal Types

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

### 5.3 Transition Execution — runEvent vs resumeEvent

```
ctx.runEvent(event, params?)
  ├─ Creates new workflow_execution (or new event_execution on existing instance)
  ├─ Evaluates context {} fresh
  ├─ Executes all transactions {}
  ├─ Runs finish {} and display {}
  └─ ctx.isResumed = false inside the event

ctx.resumeEvent(event, params?)
  ├─ Loads existing workflow_execution.context from PG (decrypted)
  ├─ Skips context {} evaluation
  ├─ Skips transactions {} execution
  ├─ Runs finish {} and display {} only
  └─ ctx.isResumed = true inside the event — branch on this for resume-specific logic
```

Multiple `runEvent` / `resumeEvent` calls in one transition are **async by default** (parallel `CompletablePromise`). Use `ctx.await(...)` to synchronize.

### 5.4 MassOperation Execution Flow

```
Trigger fires (cron / signal / manual)
  │
  ├─ Load MassOperation DSL
  ├─ Evaluate lock { ctx -> } closure
  │    └─ false → abort, log LOCKED status, stop
  │
  ├─ Insert mass_operation_execution row (status: RUNNING)
  ├─ Evaluate context {} block → save to mass_operation_execution.context
  ├─ Evaluate source { ctx -> } → get item list
  ├─ Insert mass_operation_item row per item (status: PENDING)
  │    (IDs saved here serve as the business lock scope)
  │
  └─ Start Temporal MassOpWorkflow:
       │
       ├─ For each item (parallel, up to configured concurrency limit):
       │    └─ MassOpItemActivity:
       │         ├─ Update mass_operation_item status → RUNNING
       │         ├─ Evaluate item { ctx -> } closure
       │         │    └─ ctx.runWorkflow() or ctx.runEvent()
       │         ├─ On SUCCESS → mass_operation_item status: DONE
       │         └─ On FAILURE → mass_operation_item status: FAILED
       │                         log error, continue to next item
       │
       ├─ After every `partial.every` items processed:
       │    └─ Emit PARTIAL signal with payload from signals.partial { ctx -> }
       │
       ├─ After all items processed:
       │    ├─ Emit COMPLETED signal with payload from signals.completed { ctx -> }
       │    ├─ Run finish { ctx, ex -> }
       │    └─ Update mass_operation_execution status: DONE (or DONE_WITH_FAILURES)
```

### 5.5 ExecutionContext Hierarchy

Unchanged from v0.4, with additions for mass operation:

```
BaseContext
├── (all fields from v0.4)
├── isMassOperation: Boolean
└── isResumed: Boolean             // true when called via resumeEvent

MassOperationContext extends BaseContext
├── processedCount: Long
├── failedCount: Long
├── get("item"): Map<String, Any>  // current source row
└── emit(Signal): Unit             // manual signal emission if needed

TransitionContext extends BaseContext
├── runEvent(event, params?): StepHandle
├── resumeEvent(event, params?): StepHandle
├── runWorkflow(code, action, eventNumber, params): StepHandle
├── await(vararg StepHandle): Unit
├── setStatus(state: String): Unit
└── states: Map<String, String>    // declared states, accessible by name
```

---

## 6. Workflow Lifecycle Model

Unchanged from v0.4. See section 6 of v0.4 for state machine concepts, example lifecycle diagram, and `ctx.prolong()` behavior.

---

## 7. Mass Operation Model

### 7.1 Triggers

| Trigger type | DSL | Description |
|---|---|---|
| Cron | `cron("0 1 * * *")` | Standard cron expression |
| Exact time | `once(at = "2025-12-31T23:59:00")` | Run once at specific datetime |
| Periodic | `every(days = 1)`, `every(weeks = 1)` | Repeating interval |
| External | `onSignal(Signal.external("NAME"))` | MQ message or webhook |
| Signal from op | `onSignal(Signal.from("OP_CODE", Signal.COMPLETED))` | Triggered by another mass op |
| Signal partial | `onSignal(Signal.from("OP_CODE", Signal.PARTIAL))` | Triggered on partial completion |

Multiple triggers can be declared — any one firing starts the operation (subject to lock).

### 7.2 Business Lock

The lock closure returns a `Boolean`. `true` = allowed to start. `false` = locked, operation aborted and logged.

The `mass_operation_item` table is populated at the start of each run with the IDs of all items to be processed. This serves as the definitive scope of the run — later re-runs of failed items reference the same `mass_operation_execution` row and its item list. Re-running the entire mass operation is forbidden by the framework (enforced by the lock closure + status check).

### 7.3 Signals

```
PARTIAL signal  → emitted every N items (configured per operation)
COMPLETED signal → emitted when all items processed

Signal payload  → declared in signals {} closure, available to receivers
Signal receiver → declared in triggers {} of another mass operation
               → or consumed by external systems via MQ/webhook subscription
```

### 7.4 Categories

Category is a free-form string declared in DSL. Used for:
- Admin UI grouping and filtering
- Access control (chief product owners see only their category)
- Reporting aggregation

Standard categories (not hardcoded — declared per operation):
`CREDITS`, `DEPOSITS`, `COLLATERALS`, `COLLECTIONS`, `REPORTING`

### 7.5 Failed Item Re-run

Failed items (`mass_operation_item.status = FAILED`) can be re-run individually from the admin UI:

```
POST /api/mass-operations/{executionId}/items/{itemId}/retry
```

This creates a new `mass_operation_item` row linked to the same `mass_operation_execution`, re-evaluates the `item { ctx -> }` closure for that item only, and updates the item status. The parent operation's `DONE_WITH_FAILURES` status does not change — but `failed_count` is decremented on successful retry.

Re-running the entire mass operation is forbidden. A v2 feature will allow computing unprocessed items on-the-fly and creating a new operation for them.

---

## 8. State Management

### 8.1 PostgreSQL Schema

All JSONB fields containing business data are encrypted at the application level.

```sql
-- Unchanged from v0.4
CREATE TABLE workflow_execution ( ... );
CREATE TABLE event_execution ( ... );
CREATE TABLE workflow_transition_log ( ... );

-- NEW: one row per mass operation run
CREATE TABLE mass_operation_execution (
    id                BIGSERIAL    PRIMARY KEY,
    code              VARCHAR(100) NOT NULL,
    category          VARCHAR(100) NOT NULL,
    dsl_version       VARCHAR(50)  NOT NULL,
    status            VARCHAR(30)  NOT NULL,  -- RUNNING / DONE / DONE_WITH_FAILURES / LOCKED / FAULTED
    context           JSONB        NOT NULL DEFAULT '{}',   -- encrypted, shared for all items
    total_items       BIGINT       NOT NULL DEFAULT 0,
    processed_count   BIGINT       NOT NULL DEFAULT 0,
    failed_count      BIGINT       NOT NULL DEFAULT 0,
    trigger_type      VARCHAR(50)  NOT NULL,  -- CRON / ONCE / SIGNAL_EXTERNAL / SIGNAL_FROM_OP
    trigger_source    VARCHAR(200),           -- signal source or cron expression
    performed_by      VARCHAR(200) NOT NULL,  -- user or system/scheduler
    started_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    temporal_workflow_id VARCHAR(200)
);

-- NEW: one row per item per run
CREATE TABLE mass_operation_item (
    id                       BIGSERIAL    PRIMARY KEY,
    mass_operation_execution_id BIGINT    NOT NULL REFERENCES mass_operation_execution(id),
    item_key                 VARCHAR(500) NOT NULL,  -- business identifier (e.g. agreement_id)
    item_data                JSONB        NOT NULL DEFAULT '{}',  -- encrypted source row
    status                   VARCHAR(20)  NOT NULL,  -- PENDING / RUNNING / DONE / FAILED
    workflow_execution_id    BIGINT       REFERENCES workflow_execution(id),
    error_message            TEXT,
    started_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    retry_of                 BIGINT       REFERENCES mass_operation_item(id)  -- for retried items
);

CREATE INDEX idx_mass_op_exec_code     ON mass_operation_execution(code);
CREATE INDEX idx_mass_op_exec_status   ON mass_operation_execution(status);
CREATE INDEX idx_mass_op_exec_category ON mass_operation_execution(category);
CREATE INDEX idx_mass_op_item_exec     ON mass_operation_item(mass_operation_execution_id);
CREATE INDEX idx_mass_op_item_status   ON mass_operation_item(status);
CREATE INDEX idx_mass_op_item_key      ON mass_operation_item(item_key);
```

### 8.2 Temporal Payload

Temporal holds only:
- `event_execution.id`
- `workflow_execution.id`
- `mass_operation_execution.id`
- `mass_operation_item.id` (per item activity)

All real state lives in PostgreSQL.

---

## 9. Build & Deploy Pipeline

Unchanged from v0.4. Mass operation `.mass.kts` files are compiled by the same `compileDsl` Gradle task. Semantic validation extended to cover:

- All events referenced in `item { ctx -> }` exist in registry
- All workflows referenced in `ctx.runWorkflow()` exist in registry
- All helpers used in `source {}`, `lock {}`, `context {}` resolve correctly
- Signal references (`Signal.from("OP_CODE", ...)`) resolve to a known mass operation code

---

## 10. API Contract

### 10.1 Execute Single Event (Unchanged)

```
POST /api/events/execute
{ "code", "action", "eventNumber"?, "eventParameters" }
```

### 10.2 Trigger Mass Operation Manually

```
POST /api/mass-operations/trigger
Content-Type: application/json
Authorization: Bearer {token}

{
  "code": "INTEREST_CHARGE",
  "context": {
    "date": "2025-06-01"
  }
}
```

Response:

```json
{
  "executionId": 5001,
  "code": "INTEREST_CHARGE",
  "category": "CREDITS",
  "status": "RUNNING",
  "totalItems": 84320,
  "startedAt": "2025-06-01T01:00:00Z"
}
```

### 10.3 Mass Operation Status

```
GET /api/mass-operations/{executionId}
```

```json
{
  "executionId": 5001,
  "code": "INTEREST_CHARGE",
  "status": "DONE_WITH_FAILURES",
  "totalItems": 84320,
  "processedCount": 84273,
  "failedCount": 47,
  "startedAt": "2025-06-01T01:00:00Z",
  "completedAt": "2025-06-01T03:14:22Z"
}
```

### 10.4 Mass Operation Items

```
GET /api/mass-operations/{executionId}/items?status=FAILED&page=0&size=50
```

```json
{
  "items": [
    {
      "itemId": 91234,
      "itemKey": "AGR-00198",
      "status": "FAILED",
      "errorMessage": "CreditBorrowerAccountTransaction: account frozen",
      "startedAt": "2025-06-01T01:04:11Z",
      "workflowEventNumber": 10042
    }
  ],
  "totalFailed": 47,
  "page": 0
}
```

### 10.5 Retry Failed Item

```
POST /api/mass-operations/{executionId}/items/{itemId}/retry
Authorization: Bearer {token}
```

```json
{
  "newItemId": 91890,
  "itemKey": "AGR-00198",
  "status": "RUNNING",
  "retryOf": 91234
}
```

---

## 11. Versioning Strategy

Unchanged from v0.4. DSL version locked at instance/execution creation. Mass operations follow the same strict isolation — a running mass operation continues with the DSL version it started with even if a new version deploys mid-run.

---

## 12. Admin & Stakeholder UI

### 12.1 Temporal UI Integration

Unchanged from v0.4. "View in Temporal" button per execution.

### 12.2 DSL Editing: Server-Side VSCode

Unchanged from v0.4. `cbs-rules` repo linked from admin panel header.

### 12.3 Stakeholder Display Data

Unchanged from v0.4. `display {}` block in Event/Transaction DSL.

### 12.4 Mass Operation Report UI

The admin panel provides a dedicated report view per `mass_operation_execution`:

**Summary card:**
- Operation code, category, trigger type, DSL version
- Start / end time, duration
- Total items / processed / failed (with visual progress bar)
- Status badge: `RUNNING`, `DONE`, `DONE_WITH_FAILURES`, `LOCKED`, `FAULTED`

**Item list:**
- Filterable by status (ALL / DONE / FAILED / RUNNING / PENDING)
- Per item: `item_key`, status, error message, link to `workflow_execution` detail
- Failed items have a "Retry" button (calls `POST /api/mass-operations/{id}/items/{itemId}/retry`)

**Stakeholder drill-down:**
- Click any item → opens the corresponding `workflow_execution` detail
- Shows `display_data` labels (agreement ID, customer ID, amount, etc.)
- Shows transition history and current state

**Export (v2):**
- Export item list to Excel / PDF
- Deferred to v2. Schema and data are ready.

### 12.5 Funnel / Heatmap UI

Deferred to v2. Data model ready.

---

## 13. BPMN Export

Unchanged from v0.4. Static workflow template + dynamic instance heatmap via `bpmn-js`.

```
GET /api/workflows/{code}/bpmn
GET /api/workflows/{code}/bpmn/{eventNumber}
GET /api/workflows/{code}/bpmn/aggregate
```

---

## 14. Module Structure

```
root/
├── app/
├── dsl-api/
├── dsl-runtime/
├── dsl-compiler/
├── temporal-core/
├── bpmn-export/
├── mass-operation-core/          ← NEW: MassOp workflow + activity + scheduler
└── build.gradle.kts

app/src/main/java/
├── api/
│   ├── EventController.java
│   ├── MassOperationController.java      // NEW
│   ├── BpmnController.java
│   └── DevDslController.java
├── service/
│   ├── EventService.java
│   ├── WorkflowResolver.java
│   ├── WorkflowExecutor.java
│   ├── ContextEvaluator.java
│   ├── ContextEncryptionService.java
│   ├── MassOperationService.java         // NEW
│   ├── MassOperationScheduler.java       // NEW: cron + trigger management
│   ├── SignalEmitter.java                // NEW
│   └── DslVersionService.java
├── temporal/
│   ├── EventWorkflow.java
│   ├── EventWorkflowImpl.java
│   ├── TransactionActivity.java
│   ├── TransactionActivityImpl.java
│   ├── MassOpWorkflow.java               // NEW
│   ├── MassOpWorkflowImpl.java           // NEW
│   └── MassOpItemActivity.java           // NEW
├── state/
│   ├── WorkflowExecutionRepository.java
│   ├── EventExecutionRepository.java
│   ├── WorkflowTransitionLogRepository.java
│   ├── MassOperationExecutionRepository.java  // NEW
│   ├── MassOperationItemRepository.java       // NEW
│   └── ExecutionContextImpl.java
└── dsl/
    └── DslLoader.java

dsl-api/src/main/kotlin/
├── WorkflowDefinition.kt
├── TransitionRule.kt
├── TransitionContext.kt                  // NEW: ctx for transition closures
├── EventDefinition.kt
├── TransactionDefinition.kt
├── HelperDefinition.kt
├── ConditionDefinition.kt
├── MassOperationDefinition.kt           // NEW
├── SignalDefinition.kt                  // NEW
├── TriggerDefinition.kt                 // NEW
├── SourceDefinition.kt                  // NEW
├── LockDefinition.kt                    // NEW
├── ItemDefinition.kt                    // NEW
├── ContextBlock.kt
├── DisplayBlock.kt
├── FinishBlock.kt
├── context/
│   ├── BaseContext.kt
│   ├── ParameterContext.kt
│   ├── EnrichmentContext.kt
│   ├── TransactionContext.kt
│   ├── FinishContext.kt
│   └── MassOperationContext.kt          // NEW
├── ExecutionResult.kt
├── HelperInput.kt
├── HelperOutput.kt
├── HelperFunction.kt
├── Signal.kt                            // NEW
├── SignalType.kt                        // NEW
└── Action.kt

dsl-runtime/src/main/kotlin/
├── WorkflowBuilder.kt
├── EventBuilder.kt
├── TransactionBuilder.kt
├── HelperBuilder.kt
├── ConditionBuilder.kt
├── MassOperationBuilder.kt              // NEW
├── SignalBuilder.kt                     // NEW
├── TriggerBuilder.kt                    // NEW
├── StubWorkflowGenerator.kt
├── ConditionDsl.kt
├── StepHandle.kt
└── DslRegistry.kt
```

---

## 15. Risks & Open Questions

### 15.1 Risk Table

| Risk | Description | Severity | Mitigation |
|---|---|---|---|
| Temporal worker versioning | In-flight isolation | High | Spike before v1 |
| Helper idempotency | Temporal retries may double side effects | High | Convention in v1; framework in v2 |
| Compensating transactions | Wrong rollback() breaks ledger | High | Accounting/compliance review gate |
| Partial compensation failure | Rollback fails mid-way | High | Temporal retry on rollback; per-tx rollback status |
| JSONB encryption performance | Encrypt/decrypt on every read/write | Medium | Benchmark in staging |
| prolong() loop risk | Infinite prolong chain | Medium | Terminal state check; DSL author responsibility |
| Mass op concurrency | 80k items processed in parallel may overwhelm downstream services | High | Configurable concurrency limit per mass operation (max parallel item activities) |
| Mass op lock race condition | Two scheduler instances fire simultaneously | Medium | DB-level advisory lock or unique constraint on (code, status=RUNNING) |
| Signal delivery guarantee | PARTIAL/COMPLETED signals must not be lost | Medium | Persist signals to a `mass_operation_signal` table; retry on failure |
| resumeEvent misuse | Calling resumeEvent when no prior execution exists | Medium | Framework validates workflow_execution exists before resumeEvent; throws if not found |

### 15.2 Resolved Decisions

- Context shared across transitions (no fresh seed between states)
- Preview confirmation: client calls SUBMIT independently
- Stakeholder funnel UI: v2
- prolong() depth guard: none
- compileDsl semantic validation: required, covers mass operations too
- Re-running entire mass operation: forbidden by framework
- Multiple runEvent per transition: async by default, await() to synchronize

### 15.3 Open Questions for Team Discussion

1. **Temporal worker versioning strategy**: Dedicated task queue per DSL version, or Temporal worker versioning API? Spike needed.
2. **Mass op concurrency limit**: Should it be declared in DSL, or configured per deployment environment?
3. **Signal persistence**: Where are emitted signals stored? Dedicated `mass_operation_signal` table, or reuse `workflow_transition_log`?
4. **Mass op lock implementation**: DB advisory lock vs. unique partial index on `(code, status='RUNNING')`?
5. **JSONB encryption strategy**: Field-level vs. row-level encryption? Affects query capability.
6. **Accounting review gate**: Who owns review of all `rollback()` implementations before go-live?
7. **BPMN export v1 scope**: Static template only, or dynamic instance view too?

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

### 17.1 Temporal

- [Temporal Java SDK — Workflow Versioning](https://docs.temporal.io/dev-guide/java/versioning)
- [Temporal Web UI — Self-Hosted Deployment](https://docs.temporal.io/web-ui)
- [Temporal Workflow Determinism & Versioning — Temporal Blog](https://temporal.io/blog/workflow-versioning)

### 17.2 Community

- [Как мы строили оркестрацию бизнес-процессов на Temporal (Habr)](https://habr.com/ru/articles/970730/)
- [Temporal: практика применения в продакшене (Habr)](https://habr.com/ru/articles/966972/)

### 17.3 Architecture & Patterns

- [Saga Pattern & Compensating Transactions — Microsoft Architecture Guide](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Kotlin DSL Design Patterns — JetBrains Blog](https://blog.jetbrains.com/kotlin/2021/10/kotlin-dsl-best-practices/)
- [bpmn-js — BPMN 2.0 Rendering Library](https://bpmn.io/toolkit/bpmn-js/)

---

*Document v0.5 — MassOperation DSL added, transition closures refined. Resolved decisions in 15.2. Open questions in 15.3.*
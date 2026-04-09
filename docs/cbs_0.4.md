# Technical Design Document: Business Orchestration Engine

**Version:** 0.4-draft  
**Status:** For team discussion  
**Stack:** Java 25, Spring Boot, Temporal, PostgreSQL, Kotlin Script (.kts), Gradle 9.x (multi-module), Vue + Nuxt.js (admin)  
**Date:** 2025

**Changelog v0.4:**
- `finish {}` runs on both success and failure; receives `ctx, ex` (ex is null on success)
- `ExecutionContext` split into lifecycle-scoped interfaces with common ancestor
- `ctx.complete()` renamed to `ctx.prolong()`
- `display {}` is now optional (defaults to context values); added to transactions block
- `context {}` and `transactions {}` and `finish {}` all receive `ctx ->`
- Import aliasing: `#import loan-disbursement.* as disb`; step chaining via `.then()`
- `ctx.superCall()` renamed to `ctx.delegate()` — calls interface method from within closure
- Helper chaining uses `mapOf()`; v2 typed DSL note added
- Helper interface unified: `HelperFunction<I extends HelperInput, O extends HelperOutput>`
- Inline helpers use `ctx.helper("SQL_CLIENT", mapOf(...))` — no `ctx.db.query`
- Loops/iteration inside `transactions {}` block documented
- JSONB fields encrypted at app level
- Gitea repo renamed to `cbs-rules`
- API uses `eventNumber` instead of `workflowInstanceId`
- Admin panel includes link to `cbs-rules` in server-side VSCode
- BPMN export section added (static template + dynamic instance heatmap)
- `compileDsl` semantic validation is required (not optional)
- Context shared across transitions (no fresh seed between states)
- Stakeholder funnel UI deferred to v2
- No max chain length on `ctx.prolong()`

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Core Entities](#3-core-entities)
4. [DSL Design (.kts)](#4-dsl-design-kts)
5. [Execution Model](#5-execution-model)
6. [Workflow Lifecycle Model](#6-workflow-lifecycle-model)
7. [State Management](#7-state-management)
8. [Build & Deploy Pipeline](#8-build--deploy-pipeline)
9. [API Contract](#9-api-contract)
10. [Versioning Strategy](#10-versioning-strategy)
11. [Admin & Stakeholder UI](#11-admin--stakeholder-ui)
12. [BPMN Export](#12-bpmn-export)
13. [Module Structure](#13-module-structure)
14. [Risks & Open Questions](#14-risks--open-questions)
15. [Out of Scope (v1)](#15-out-of-scope-v1)
16. [References](#16-references)

---

## 1. Overview & Goals

### 1.1 Context

The system replaces an existing Spring-bean-based orchestration engine (Event/Transaction/Helper) that suffers from slow startup, no parallelism, unreliable state persistence, and business rules locked in Java code.

The new system preserves the existing mental model but replaces the runtime with Temporal, PostgreSQL for state, and Kotlin Script (`.kts`) for business-editable DSL files. Every execution — regardless of whether it uses a full workflow lifecycle or is a single stateless event — produces a persistent artifact visible to stakeholders and admins.

### 1.2 Goals

- **Business autonomy**: analysts write and modify DSL files without developer involvement
- **Correctness**: every execution instance runs to completion on the DSL version it started with
- **Parallelism**: all steps run as Temporal `CompletablePromise`s; `await()` marks explicit sync points
- **Full observability**: every execution produces a `workflow_execution` artifact with transition log, context, and stakeholder display data
- **Lifecycle visibility**: BPMN-based route visualization and funnel/heatmap UI for stakeholders
- **Unified model**: workflowless events are syntactic sugar — a stub workflow with one state

### 1.3 Unified Execution Model

There is no runtime distinction between stateless and workflow mode. Every event execution is backed by a workflow instance. For events without an explicit `workflow {}` DSL, the framework generates a stub workflow automatically:

```
stub workflow:
  states { COMPLETED }
  initial = "COMPLETED"
  transitions {
    _ -> COMPLETED on Action.SUBMIT runs event(thisEvent)
  }
  terminalStates { COMPLETED }
```

`workflow_execution_id` is never null in `event_execution`. All executions appear in admin and stakeholder UI uniformly.

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
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                   Spring Boot Application                        │
│                                                                  │
│  EventController → EventService → WorkflowResolver              │
│                         │                                        │
│                         ▼                                        │
│                  WorkflowExecutor                                │
│                  ├─ context {} evaluator  (pre-Temporal, PG)     │
│                  ├─ transition validator                          │
│                  └─ TemporalClient                               │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                 DSL Runtime Module                     │     │
│  │  WorkflowDefinition / EventDefinition /                │     │
│  │  TransactionDefinition / HelperDefinition /            │     │
│  │  ConditionDefinition / ContextBlock / FinishBlock      │     │
│  └────────────────────────────────────────────────────────┘     │
└────────────────────────────┬─────────────────────────────────────┘
                             │
               ┌─────────────┴──────────────┐
               ▼                            ▼
┌──────────────────────┐     ┌──────────────────────────────────┐
│   Temporal Server    │     │          PostgreSQL               │
│                      │     │                                  │
│  EventWorkflow       │     │  workflow_execution  (encrypted) │
│  TransactionActivity │     │  workflow_transition_log         │
│  (holds PKs only)    │     │  event_execution     (encrypted) │
└──────────────────────┘     └──────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│              Vue + Nuxt.js Admin Panel               │
│  ├─ Execution detail (display_data, state, history)  │
│  ├─ BPMN viewer (bpmn-js: static + heatmap)          │
│  ├─ Temporal UI link (per execution)                 │
│  └─ cbs-rules VSCode server link (DSL editing)       │
└──────────────────────────────────────────────────────┘

┌───────────────────────────┐   ┌──────────────────────────┐
│  Gitea: cbs-rules repo    │   │  GitLab CI / Jenkins     │
│  .kts files only          │   │  compiles DSL → Docker   │
└───────────────────────────┘   └──────────────────────────┘
```

---

## 3. Core Entities

### 3.1 Workflow

Top-level DSL entity. Defines a state machine. `states`, `initial`, and `terminalStates` are optional — the framework provides defaults when omitted (useful for simple single-step events wrapped in stub workflow sugar).

```
Workflow
├── code: String
├── states: List<String>          // optional, inferred from transitions if omitted
├── initial: String               // optional, defaults to first declared state
├── transitions: List<TransitionRule>
└── terminalStates: List<String>  // optional, defaults to states with no outgoing transitions
```

A `TransitionRule`:
- `from` / `to` state
- `on` Action
- `runs` Event
- `onFault` target state (defaults to `"FAULTED"`)

### 3.2 Transaction

Temporal Activity. Declares three methods via interface. In DSL, all three closures are optional — if omitted, the framework calls the corresponding method on the Spring bean directly. When a closure is declared, it can optionally call `ctx.delegate()` to also invoke the interface method.

```java
public interface Transaction {
    ExecutionResult preview(TransactionContext ctx);
    ExecutionResult execute(TransactionContext ctx);
    void rollback(TransactionContext ctx);
}
```

`ExecutionResult` is a typed wrapper (see section 3.5). In banking, `rollback` is a **compensating transaction** — a real counter-entry, not a technical undo.

### 3.3 Helper

Pure Spring bean. Always implements `HelperFunction<I extends HelperInput, O extends HelperOutput>`. Input and output are typed wrapper objects — global interfaces shared across all helpers. This makes the annotation processor straightforward: it reads `Input`/`Output` records and generates Kotlin DSL bindings.

Two categories:
- **Code helpers**: Java/Kotlin Spring beans annotated with `@Helper`. DSL entries generated via annotation processor, pushed to Nexus via Gradle (v2). In v1, referenced by string name.
- **Inline helpers**: Declared directly in `.helper.kts` DSL files as SQL calls or HTTP calls via other helpers. Scoped to a specific event folder or globally available.

The difference between global and local helper files is **scope only** — declaration syntax is identical in both. Global helpers in `global/*.helper.kts` are available to all events. Local helpers in `{event-folder}/*.helper.kts` are available only within that event folder.

### 3.4 Context Block

Evaluated **before** Temporal starts, in the Spring service layer. Resolves parameters into enriched context values saved to `workflow_execution.context` JSONB (encrypted at app level). If evaluation fails, execution is faulted before Temporal is called — status written to DB, Temporal never starts.

Context is **shared across all transitions** of the same workflow instance. Each subsequent transition reads the accumulated context from the previous state — no fresh seed between states.

### 3.5 Shared Result Types

```java
// Shared across all Transactions and Helpers
public interface HelperInput {}
public interface HelperOutput {}

public record ExecutionResult(
    boolean success,
    String  transactionCode,
    Object  output,           // stored in event_execution.executed_transactions[]
    String  errorMessage      // null on success
) {
    public static ExecutionResult success(String code, Object output) { ... }
    public static ExecutionResult failure(String code, String message) { ... }
}

// Every Helper implements this
public interface HelperFunction<I extends HelperInput, O extends HelperOutput> {
    O apply(I input);
}
```

Example Helper with typed I/O:

```java
@Helper("ACCOUNT_BY_TYPE_CURRENCY")
public class AccountByTypeCurrencyHelper
        implements HelperFunction<AccountByTypeCurrencyHelper.Input,
                                  AccountByTypeCurrencyHelper.Output> {

    public record Input(
        String accountTypeCode,
        String currencyCode,
        String customerCode
    ) implements HelperInput {}

    public record Output(
        String iban,
        String accountName,
        String status
    ) implements HelperOutput {}

    @Override
    public Output apply(Input input) { ... }
}
```

In v1, the DSL engine maps string key-value pairs from `mapOf(...)` to `Input` via reflection. In v2, the annotation processor generates typed DSL bindings — no string keys needed, just constructor arguments.

---

## 4. DSL Design (.kts)

### 4.1 File & Folder Convention

Each event owns a folder. All DSL objects for that event live in the same folder — analogous to a Java package.

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
└── loan-contract.workflow.kts              ← workflow at root, references event folders
```

### 4.2 Import System

Each DSL file declares imports. Imports resolve to other DSL files or framework interfaces. Import aliases are supported with `as`. The `compileDsl` Gradle task validates all imports — missing references fail the build.

```kotlin
// Import all objects from an event folder
#import loan-disbursement.* as disb

// Import a specific helper file
#import global.banking-helpers

// Import framework types
#import framework.ExecutionContext
#import framework.Action
```

### 4.3 Workflow DSL

`states`, `initial`, and `terminalStates` are optional. If omitted, states are inferred from transition declarations; `initial` defaults to the first `from` state; `terminalStates` defaults to states with no outgoing transitions.

```kotlin
// loan-contract.workflow.kts
#import loan-onboarding.*
#import loan-disbursement.* as disb
#import loan-cancellation.*
#import loan-closure.*
#import global.banking-helpers

workflow("LOAN_CONTRACT") {

    // Optional — inferred from transitions if omitted
    states { DRAFT, ENTERED, ACTIVE, CANCELLED, CLOSED, FAULTED }
    initial = "ENTERED"
    terminalStates { CLOSED, CANCELLED }

    transitions {
        ENTERED -> ACTIVE    on Action.APPROVE  runs event("LOAN_DISBURSEMENT")  onFault "FAULTED"
        ENTERED -> CANCELLED on Action.CANCEL   runs event("LOAN_CANCELLATION")
        ENTERED -> ENTERED   on Action.REJECT   runs event("LOAN_REJECTION_NOTICE")

        ACTIVE  -> CLOSED    on Action.CLOSE    runs event("LOAN_CLOSURE")
        ACTIVE  -> CANCELLED on Action.CANCEL   runs event("LOAN_EARLY_TERMINATION")

        FAULTED -> ENTERED   on Action.ROLLBACK runs event("LOAN_FAULT_COMPENSATION")
    }
}
```

### 4.4 Event DSL

```kotlin
// loan-disbursement/loan-disbursement.event.kts
#import loan-disbursement.* as disb
#import global.banking-helpers

event("LOAN_DISBURSEMENT") {

    parameters {
        required("customerId")
        required("loanId")
        required("amount")
        optional("accountNumber")
    }

    // Pre-Temporal enrichment. ctx already has all parameters.
    // Faults immediately if any line throws.
    context { ctx ->
        ctx["customerCode"]   = ctx.helper("FIND_CUSTOMER_CODE_BY_ID",
                                    mapOf("id" to ctx["customerId"]))
        ctx["accountCode"]    = ctx["accountNumber"] ?: "nil"
        ctx["loanConditions"] = ctx.helper("LOAN_CONDITIONS_BY_ID",
                                    mapOf("loanId" to ctx["loanId"]))
    }

    // Optional. If omitted, everything in context is shown.
    display { ctx ->
        label("Customer ID", ctx["customerId"])
        label("Loan ID",     ctx["loanId"])
        label("Amount",      ctx["amount"])
        label("Account",     ctx["accountCode"])
    }

    transactions { ctx ->

        // All steps launch as Temporal CompletablePromises immediately.
        // .then() chains a step that starts after its predecessor completes.
        // await() is an explicit barrier for a group.

        val compliance = ctx.step(disb.KycCheckTransaction)
            .then(disb.BlacklistCheckTransaction)   // starts after KYC completes

        val scoring = ctx.step(disb.CreditScoringTransaction)

        ctx.await(compliance, scoring)              // wait for both chains

        // Conditional step — when/then/otherwise
        val debit = ctx.step {
            when { ctx["loanConditions"] != null } then {
                transaction(disb.DebitFundingAccountTransaction)
            } otherwise {
                transaction(disb.DebitFallbackAccountTransaction)
            }
        }

        // Named condition definition (see section 4.9)
        val credit = ctx.step {
            when { ctx.condition(disb.BorrowerAccountReadyCondition) } then {
                transaction(disb.CreditBorrowerAccountTransaction)
            }
        }

        ctx.await(debit)
        ctx.await(credit)

        val posting = ctx.step(disb.PostDisbursementEntryTransaction)
        ctx.await(posting)

        // Passing variables into a step
        ctx.step(disb.NotificationTransaction, mapOf("channel" to "SMS"))

        // display inside transactions — controls what this step shows in UI
        display { ctx ->
            label("Debit TX",  ctx.transactionResult("DEBIT_FUNDING_ACCOUNT")["txId"])
            label("Credit TX", ctx.transactionResult("CREDIT_BORROWER_ACCOUNT")["txId"])
        }
    }

    // Runs on both success and failure. ex is null on success.
    finish { ctx, ex ->
        if (ex != null) {
            ctx.helper("SEND_FAULT_NOTIFICATION", mapOf(
                "customerId" to ctx["customerId"],
                "error"      to ex.message
            ))
        } else {
            ctx.helper("SEND_DISBURSEMENT_NOTIFICATION", mapOf(
                "customerId" to ctx["customerId"],
                "amount"     to ctx["amount"]
            ))
            // Triggers next workflow transition internally, no external API call
            ctx.prolong(Action.APPROVE)
        }
    }
}
```

### 4.5 Transaction DSL

Preview and rollback closures are optional. If omitted, the framework calls the corresponding method on the Spring bean directly. When declared, `ctx.delegate()` explicitly calls the interface method in addition to the closure body — giving fine-grained control over whether to extend or replace the bean's default behavior.

```kotlin
// loan-disbursement/debit-funding-account.transaction.kts
#import loan-disbursement.* as disb
#import global.banking-helpers

transaction("DEBIT_FUNDING_ACCOUNT") {

    // Optional. If omitted, calls DebitFundingAccountTransaction.preview()
    preview { ctx ->
        val account = ctx.helper("FIND_BANK_ACCOUNT",
            mapOf("iban" to ctx["accountCode"]))
        ExecutionResult.success("DEBIT_FUNDING_ACCOUNT", mapOf(
            "description" to "Will debit ${account["iban"]}",
            "amount"      to ctx["amount"],
            "currency"    to ctx["loanConditions"]["currency"]
        ))
    }

    execute { ctx ->
        // ctx["..."] reads from required/optional parameters
        val amount   = ctx["amount"]
        val currency = ctx["loanConditions"]["currency"]   // from context {} block

        // Reading output from an earlier transaction in same event
        val kycVerified = ctx.transactionResult("KYC_CHECK")["verified"]

        // Resolve Spring bean directly — no field injection in DSL
        val result = ctx.resolve(disb.DebitFundingAccountTransaction::class)
            .debit(iban = ctx["accountCode"], amount = amount, currency = currency)

        ctx["debitTxId"] = result["transactionId"]

        ExecutionResult.success("DEBIT_FUNDING_ACCOUNT", result)
    }

    // Optional. If omitted, calls DebitFundingAccountTransaction.rollback()
    rollback { ctx ->
        // ctx.delegate() calls the interface method first, then continues below.
        // Omit ctx.delegate() to fully replace the interface method.
        ctx.delegate()

        // Additional compensating logic on top of the default implementation
        ctx.resolve(disb.DebitFundingAccountTransaction::class)
            .postCompensatingEntry(
                originalTxId = ctx["debitTxId"],
                swapAccounts = true
            )
    }
}
```

**Reading values in DSL — reference table:**

| Source | DSL syntax |
|---|---|
| Required parameter | `ctx["customerId"]` |
| Optional parameter | `ctx["accountNumber"] ?: "default"` |
| Pre-evaluated context value | `ctx["customerCode"]` (from `context {}`) |
| Earlier transaction output | `ctx.transactionResult("KYC_CHECK")["verified"]` |
| Helper call | `ctx.helper("NAME", mapOf("key" to value))` |
| Chained helper | `ctx.helper("OUTER", mapOf("x" to ctx.helper("INNER", mapOf(...))))` |
| Spring bean | `ctx.resolve(MyBean::class).myMethod()` |
| Current action | `ctx.action` |
| Event code | `ctx.eventCode` |
| Event number (PK) | `ctx.eventNumber` |
| Workflow state | `ctx.workflowState` |
| Workflow instance ID | `ctx.workflowInstanceId` |
| Call interface method | `ctx.delegate()` |

### 4.6 Helper Chaining DSL

Helpers are always `HelperFunction<I, O>`. In DSL they are called via `ctx.helper("NAME", mapOf(...))` and can be chained — output of one helper becomes a value in the `mapOf` of the next.

In v1, keys are strings. In v2, annotation processor generates typed constructors — no string keys, just positional arguments.

```kotlin
// Chained helper call inside a transaction or context block
val accountName = ctx.helper("ACCOUNT_BY_TYPE_CURRENCY", mapOf(
    "ACCOUNT_TYPE_CODE" to "INTERNAL",
    "CURRENCY_CODE"     to ctx.helper("CURRENCY_BY_AGREEMENT", mapOf(
                                "agreementId" to ctx["agreementId"]
                            )),
    "CUSTOMER_CODE"     to ctx.transactionResult("TR_ONBOARDING")["customerCode"]
))

ctx["fundingAccount"] = accountName

// v2 (after annotation processor generates typed bindings):
// val accountName = ctx.helper(AccountByTypeCurrency(
//     accountTypeCode = "INTERNAL",
//     currencyCode    = ctx.helper(CurrencyByAgreement(ctx["agreementId"])),
//     customerCode    = ctx.transactionResult("TR_ONBOARDING")["customerCode"]
// ))
```

### 4.7 Helper DSL Files

```kotlin
// loan-disbursement/loan-helpers.helper.kts
#import loan-disbursement.* as disb
#import framework.HelperContext

helpers {

    // Inline helper using SQL_CLIENT helper (no ctx.db.query — use helper instead)
    helper("LOAN_CONDITIONS_BY_ID") { ctx ->
        val myLoanId = ctx.params["loanId"]
        ctx.helper("CURRENCY_BY_AGREEMENT", mapOf(
            "agreementId" to ctx.params["agreementId"],
            "userLoanId"  to myLoanId,
            "amount"      to ctx.resolve(AccountService::class).calculateAmount(
                ctx.helper("SQL_CLIENT", mapOf(
                    "QUERY"  to "SELECT * FROM loan_conditions WHERE loan_id = :loanId",
                    "PARAMS" to mapOf("loanId" to myLoanId)
                ))
            )
        ))
        // Last expression is implicitly returned — no `return` keyword needed
    }

    // Inline HTTP helper
    helper("KYC_STATUS_BY_CUSTOMER") { ctx ->
        ctx.helper("HTTP_GET", mapOf(
            "url" to "https://kyc-service/api/status/${ctx.params["customerId"]}"
        ))
    }
}
```

```kotlin
// global/banking-helpers.helper.kts
#import framework.HelperContext
#import banking.AccountRepository
#import banking.LoanProductRepository
#import banking.CustomerRepository

helpers {
    // Reference existing Spring beans — same declaration syntax as local helpers
    // The only difference: these are available to all events
    helper("FIND_BANK_ACCOUNT") { ctx ->
        ctx.resolve(AccountRepository::class).findByIban(ctx.params["iban"] as String)
    }

    helper("FIND_CUSTOMER_CODE_BY_ID") { ctx ->
        ctx.resolve(CustomerRepository::class).findCodeById(ctx.params["id"] as String)
    }

    helper("CURRENCY_BY_AGREEMENT") { ctx ->
        ctx.resolve(LoanProductRepository::class)
            .findCurrencyByAgreement(ctx.params["agreementId"] as String)
    }
}
```

### 4.8 Conditional Transactions & Loops

Conditions use `when/then/orWhen/otherwise` inside a step. A named `Condition` DSL object can be declared separately and reused.

```kotlin
transactions { ctx ->

    // Inline condition
    val debit = ctx.step {
        when { ctx["loanConditions"] != null } then {
            transaction(disb.DebitFundingAccountTransaction)
        } otherwise {
            transaction(disb.DebitFallbackAccountTransaction)
        }
    }

    // Named condition reference (see section 4.9)
    val credit = ctx.step {
        when { ctx.condition(disb.BorrowerAccountReadyCondition) } then {
            transaction(disb.CreditBorrowerAccountTransaction)
        }
    }

    // Multi-branch
    val routing = ctx.step {
        when   { ctx["amount"].toLong() > 10_000_000 } then {
            transaction(disb.HighValueDebitTransaction)
        } orWhen { ctx["currency"] == "USD" } then {
            transaction(disb.ForeignCurrencyDebitTransaction)
        } otherwise {
            transaction(disb.StandardDebitTransaction)
        }
    }

    ctx.await(debit, credit, routing)

    // Loop — dynamic transaction list from a helper/SQL result
    val trCodes = ctx.helper("SQL_CLIENT", mapOf(
        "QUERY"  to "SELECT code FROM pending_transactions WHERE event_id = :id",
        "PARAMS" to mapOf("id" to ctx.eventNumber)
    )) as List<String>

    val dynamicSteps = trCodes.map { trCode ->
        ctx.println("Scheduling transaction: $trCode")
        ctx.step { transaction(trCode) }
    }

    // Await all dynamic steps
    ctx.await(*dynamicSteps.toTypedArray())
}
```

### 4.9 Condition DSL

Named conditions are declared as standalone DSL objects and referenced in `when { ctx.condition(...) }`.

```kotlin
// loan-disbursement/borrower-account-ready.condition.kts
#import loan-disbursement.* as disb
#import global.banking-helpers

condition("BORROWER_ACCOUNT_READY") { ctx ->
    val account = ctx.helper("FIND_BANK_ACCOUNT",
        mapOf("iban" to ctx["accountCode"]))
    account != null && account["status"] == "ACTIVE"
}
```

---

## 5. Execution Model

### 5.1 Action Enum

```java
public enum Action {
    PREVIEW,   // Dry run — no state change, no execution
    SUBMIT,    // First execution or any forward transition
    APPROVE,   // Advance to approved/active state
    REJECT,    // Operator or system rejection
    CANCEL,    // Customer-initiated or business cancellation
    CLOSE,     // Natural end of lifecycle
    ROLLBACK   // Manual compensating action
}
```

### 5.2 ExecutionContext Interface Hierarchy

Context is split by lifecycle phase. Each block in the DSL receives the appropriate scoped context — preventing misuse (e.g. calling `transactionResult()` from a `context {}` block, or `put()` from a `finish {}` block).

```
BaseContext
├── getEventCode(): String
├── getEventNumber(): Long
├── getDslVersion(): String
├── getAction(): Action
├── getWorkflowInstanceId(): Long
├── getWorkflowState(): String
├── getWorkflowCode(): String
├── resolve(Class<T>): T                 // Spring bean access
└── helper(String, Map<String, Any>): Any

ParameterContext extends BaseContext
└── get(String): Any                     // read required/optional params

EnrichmentContext extends ParameterContext
└── put(String, Any): Unit               // write to context (context {} block only)

TransactionContext extends EnrichmentContext
├── transactionResult(String): Any       // read output of earlier tx
├── step(Transaction, Map?): StepHandle  // launch a step
├── await(vararg StepHandle): Unit       // sync barrier
├── condition(Condition): Boolean        // evaluate named condition
├── delegate(): Unit                     // call interface method from closure
└── println(String): Unit               // debug logging

FinishContext extends TransactionContext
├── prolong(Action): Unit                // trigger next transition internally
└── getException(): Throwable?           // null on success, populated on failure
```

`finish { ctx, ex ->` receives `FinishContext` as `ctx` and `Throwable?` as `ex` (null on success).

### 5.3 Execution Flow

```
POST /api/events/execute
  { code, action, eventNumber?, eventParameters, userId }
  │
  ├─ Load DSL (compiled JAR)
  ├─ Resolve workflow: declared or stub
  ├─ SUBMIT + no eventNumber → create workflow_execution row
  ├─ Subsequent actions → load workflow_execution by eventNumber
  ├─ Validate transition: currentState + action → TransitionRule
  │    └─ Invalid → 422, no DB writes
  ├─ Validate required parameters
  │
  ├─ Evaluate context {} (pre-Temporal, Spring layer):
  │    ├─ Seed EnrichmentContext with accumulated context from workflow_execution
  │    │   (context is shared across transitions — no fresh seed)
  │    ├─ Run ctx[] assignments in order
  │    ├─ Persist enriched context → workflow_execution.context (encrypted)
  │    └─ On failure → FAULTED status, HTTP error, stop
  │
  ├─ Insert event_execution row (FK → workflow_execution, never null)
  ├─ Insert workflow_transition_log row (status: RUNNING)
  │
  └─ Start Temporal Workflow:
       │
       ├─ All steps launch as CompletablePromises immediately
       ├─ .then() chains sequential steps within a promise chain
       ├─ await() blocks until named promises complete
       │
       ├─ On SUCCESS:
       │    ├─ Run finish { ctx, ex=null }
       │    │    └─ If ctx.prolong(action) called → trigger next transition
       │    │       in a separate thread after a short delay (no network round-trip)
       │    ├─ Update workflow_execution.current_state → transition.to
       │    ├─ Update workflow_transition_log → COMPLETED
       │    └─ If terminal state → workflow_execution.status = CLOSED
       │
       └─ On FAILURE:
            ├─ Run finish { ctx, ex=<exception> }
            │    └─ Custom fault handling, notifications, etc.
            ├─ Auto-rollback executed transactions (reverse order, compensating entries)
            ├─ Rollback to last checkpoint (previous stable state)
            ├─ Update workflow_execution.current_state → transition.onFault
            ├─ Update workflow_transition_log → FAULTED + fault_message
            └─ Stop. No auto-retry. Recovery via explicit DSL action.
```

---

## 6. Workflow Lifecycle Model

### 6.1 State Machine Concepts

| Concept | Description |
|---|---|
| `states` | Optional. Inferred from transitions if omitted. |
| `initial` | Optional. Defaults to first `from` state in transitions. |
| `terminalStates` | Optional. Defaults to states with no outgoing transitions. |
| `FAULTED` | Framework-reserved. Set on auto-rollback after failure. |
| `onFault` | Per-transition. Target state on failure. Defaults to `"FAULTED"`. |
| Stub workflow | Auto-generated for events with no explicit workflow DSL. One state: COMPLETED. |
| Context sharing | `workflow_execution.context` is shared across all transitions. No fresh seed. |

### 6.2 Example Lifecycle: Loan Contract

```
  [PREVIEW — no persisted state]
        │
        │ SUBMIT
        ▼
    ENTERED ◄──────────────────────────── FAULTED
        │                                    ▲
        │ APPROVE (or ctx.prolong(APPROVE))  │ (auto on failure)
        ▼                                    │
    ACTIVE ──────────────────────────────────┘
        │
        ├── CANCEL ──► CANCELLED (terminal)
        └── CLOSE  ──► CLOSED   (terminal)

    ENTERED ── CANCEL  ──► CANCELLED (terminal)
    ENTERED ── REJECT  ──► ENTERED   (stays, notice sent)
    FAULTED ── ROLLBACK ──► ENTERED  (manual recovery)
```

### 6.3 Auto-Advance via ctx.prolong()

When `ctx.prolong(action)` is called inside `finish {}`, the framework triggers the next workflow transition in a separate thread after a short delay — without a network round-trip or external API call. This is the integration point for legacy system behavior where certain state progressions were automatic.

There is no maximum chain depth enforced by the framework. DSL authors are responsible for avoiding infinite prolong loops. Terminal states are always checked before triggering — `prolong()` on a terminal state is a no-op.

---

## 7. State Management

### 7.1 PostgreSQL Schema

JSONB fields containing business data (`context`, `display_data`, `executed_transactions`) are encrypted at the application level before persistence. Encryption/decryption is handled by a dedicated service layer — the database stores ciphertext only.

```sql
CREATE TABLE workflow_execution (
    id                BIGSERIAL    PRIMARY KEY,
    workflow_code     VARCHAR(100) NOT NULL,
    dsl_version       VARCHAR(50)  NOT NULL,
    current_state     VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,        -- ACTIVE / CLOSED / FAULTED
    context           JSONB        NOT NULL DEFAULT '{}',        -- encrypted
    display_data      JSONB        NOT NULL DEFAULT '{}',        -- encrypted
    performed_by      VARCHAR(200) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE event_execution (
    id                    BIGSERIAL    PRIMARY KEY,
    event_code            VARCHAR(100) NOT NULL,
    dsl_version           VARCHAR(50)  NOT NULL,
    action                VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    context               JSONB        NOT NULL DEFAULT '{}',    -- encrypted
    executed_transactions JSONB        NOT NULL DEFAULT '[]',    -- encrypted
    temporal_workflow_id  VARCHAR(200),
    workflow_execution_id BIGINT       NOT NULL REFERENCES workflow_execution(id),
    performed_by          VARCHAR(200) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ
);

CREATE TABLE workflow_transition_log (
    id                    BIGSERIAL    PRIMARY KEY,
    workflow_execution_id BIGINT       NOT NULL REFERENCES workflow_execution(id),
    event_execution_id    BIGINT       REFERENCES event_execution(id),
    action                VARCHAR(20)  NOT NULL,
    from_state            VARCHAR(100) NOT NULL,
    to_state              VARCHAR(100),
    status                VARCHAR(20)  NOT NULL,    -- RUNNING / COMPLETED / FAULTED
    fault_message         TEXT,
    dsl_version           VARCHAR(50)  NOT NULL,
    performed_by          VARCHAR(200) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ
);

CREATE INDEX idx_workflow_execution_code    ON workflow_execution(workflow_code);
CREATE INDEX idx_workflow_execution_status  ON workflow_execution(status);
CREATE INDEX idx_workflow_execution_user    ON workflow_execution(performed_by);
CREATE INDEX idx_transition_log_workflow_id ON workflow_transition_log(workflow_execution_id);
CREATE INDEX idx_transition_log_status      ON workflow_transition_log(status);
CREATE INDEX idx_transition_log_user        ON workflow_transition_log(performed_by);
CREATE INDEX idx_event_execution_workflow   ON event_execution(workflow_execution_id);
CREATE INDEX idx_event_execution_user       ON event_execution(performed_by);
```

### 7.2 Temporal Payload

Temporal holds only `event_execution.id` and `workflow_execution.id`. All real state lives in PostgreSQL.

### 7.3 Context Evaluation (Pre-Temporal)

1. Load accumulated `workflow_execution.context` (decrypt, deserialize)
2. Seed `EnrichmentContext` — parameters from API request merged on top
3. Evaluate each `ctx[key] = ...` in the `context {}` block in order
4. Helper calls in `context {}` are synchronous Spring bean calls (no Temporal)
5. Persist enriched context back to `workflow_execution.context` (encrypt, serialize)
6. On failure → `FAULTED` status, HTTP `CONTEXT_FAULT` error, stop

---

## 8. Build & Deploy Pipeline

### 8.1 Gitea: cbs-rules

- Stores `.kts` files only. No build logic, no app config.
- Repository: `cbs-rules`
- Branch strategy: `main` = production. Feature branches for new/changed rules.
- Import resolution and semantic validation enforced at compile time — broken imports, missing referenced events/helpers, undeclared transition target states all fail the build.

### 8.2 CI/CD Flow

```
cbs-rules Gitea: push to branch
  │
  └─► GitLab CI / Jenkins
        │
        ├─ Gradle: downloadDsl
        │    └─ Clone DSL branch from cbs-rules (fallback to main)
        │
        ├─ Gradle: compileDsl
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
        ├─ Gradle: buildApp
        │    └─ Bundle dsl-rules JAR into application
        │
        └─ Docker build → push → deploy
```

### 8.3 Dev Mode

Dev mode changes **compilation only** — Temporal is still required and running. The dev endpoint skips the CI/CD compile step and uses `javax.script` to evaluate `.kts` at runtime for fast feedback. State is persisted normally. Temporal is invoked normally.

```
POST /dev/dsl/execute   (@Profile("dev") only)
{
  "dslContent": "event(\"TEST\") { ... }",
  "eventCode": "TEST",
  "action": "SUBMIT",
  "eventParameters": {},
  "userId": "dev-user"
}
```

---

## 9. API Contract

### 9.1 Execute

```
POST /api/events/execute
Content-Type: application/json
Authorization: Bearer {token}

{
  "code": "LOAN_CONTRACT",
  "action": "APPROVE",
  "eventNumber": 10042,
  "eventParameters": {
    "customerId": "C-001",
    "loanId":     "L-9981",
    "amount":     "500000",
    "currency":   "KZT"
  }
}
```

`eventNumber` — the public-facing identifier for a workflow instance. Maps to `workflow_execution.id` internally. Required for all actions except first `SUBMIT`. `workflowInstanceId` is never exposed in the API.

`action` defaults to `SUBMIT` if omitted. `userId` resolved from JWT/session.

### 9.2 Success Response

```json
{
  "eventNumber": 10042,
  "eventCode": "LOAN_DISBURSEMENT",
  "dslVersion": "1.5.0-a3f91bc",
  "action": "APPROVE",
  "status": "DONE",
  "workflow": {
    "workflowCode":  "LOAN_CONTRACT",
    "previousState": "ENTERED",
    "currentState":  "ACTIVE"
  },
  "display": {
    "Customer ID": "C-001",
    "Loan ID":     "L-9981",
    "Amount":      "500000",
    "Account":     "KZ123456789"
  },
  "results": [
    { "transaction": "KYC_CHECK",                "status": "EXECUTED" },
    { "transaction": "DEBIT_FUNDING_ACCOUNT",    "status": "EXECUTED" },
    { "transaction": "CREDIT_BORROWER_ACCOUNT",  "status": "EXECUTED" },
    { "transaction": "POST_DISBURSEMENT_ENTRY",  "status": "EXECUTED" }
  ]
}
```

### 9.3 Fault Response

```json
{
  "eventNumber": 10043,
  "action": "APPROVE",
  "status": "FAULTED",
  "workflow": {
    "previousState": "ENTERED",
    "currentState":  "FAULTED",
    "faultMessage":  "CreditBorrowerAccountTransaction: account frozen"
  },
  "compensated": ["DEBIT_FUNDING_ACCOUNT"]
}
```

### 9.4 Validation Errors

```json
{ "error": "INVALID_TRANSITION",  "currentState": "ACTIVE",   "action": "SUBMIT" }
{ "error": "MISSING_PARAMETERS",  "missing": ["loanId"] }
{ "error": "CONTEXT_FAULT",       "message": "FIND_CUSTOMER_CODE_BY_ID: not found" }
```

---

## 10. Versioning Strategy

### 10.1 Principle

**Strict isolation. No interop.**

Every execution runs to completion on the DSL version it started with. `workflow_execution.dsl_version` is the authority. New instances always use the latest compiled version. Context is shared across transitions of the same instance regardless of version upgrades — the version locked at instance creation applies throughout.

### 10.2 Version Format

`{semver}-{gitCommitShort}` e.g. `1.5.0-a3f91bc`. Embedded in compiled JAR manifest.

### 10.3 Temporal Workflow ID

```
{eventCode}-{eventNumber}-{dslVersion}
```

Temporal `Workflow.getVersion()` guards against structural changes. Old workers drain in-flight workflows. New workers serve new starts.

---

## 11. Admin & Stakeholder UI

### 11.1 Temporal UI Integration

For v1: direct link from admin panel to Temporal Web UI per execution.

```
{temporal-ui-host}/namespaces/default/workflows/{temporal_workflow_id}
```

Surfaced as a "View in Temporal" button on the execution detail page. `temporal_workflow_id` is stored in `event_execution`.

For v2: `<iframe>` embed with CORS/CSP configuration on Temporal UI side.

### 11.2 DSL Editing: Server-Side VSCode

The admin panel includes a link to open `cbs-rules` in a server-side VSCode instance (e.g. code-server or Gitea's built-in editor with `.kts` language support). This gives analysts and developers a browser-based IDE with autocomplete driven by the compiled `dsl-runtime` types — no local tooling required.

```
{code-server-host}/?folder=/workspace/cbs-rules
```

Surfaced as an "Edit DSL Rules" button in the admin panel header.

### 11.3 Stakeholder Display Data

The `display {}` block populates `workflow_execution.display_data` JSONB (encrypted). This is shown in admin UI cards alongside state machine visualization — business-meaningful labels only, not raw context.

If `display {}` is omitted from an Event DSL, the full `context {}` output is used as display data by default.

### 11.4 Funnel / Heatmap UI (v2)

Data source: `workflow_transition_log` + `workflow_execution`. Deferred to v2. Schema is ready.

Planned queries:

```sql
-- Current state distribution
SELECT current_state, COUNT(*) FROM workflow_execution
WHERE workflow_code = 'LOAN_CONTRACT' GROUP BY current_state;

-- Transition funnel
SELECT from_state, to_state, status, COUNT(*)
FROM workflow_transition_log wtl
JOIN workflow_execution we ON we.id = wtl.workflow_execution_id
WHERE we.workflow_code = 'LOAN_CONTRACT'
GROUP BY from_state, to_state, status;

-- Average time per state
SELECT from_state, AVG(EXTRACT(EPOCH FROM (completed_at - created_at))) as avg_sec
FROM workflow_transition_log
WHERE workflow_execution_id IN (
    SELECT id FROM workflow_execution WHERE workflow_code = 'LOAN_CONTRACT'
) GROUP BY from_state;
```

---

## 12. BPMN Export

### 12.1 Purpose

Workflow definitions and execution instances can be exported as BPMN 2.0 XML and visualized via `bpmn-js` in the admin panel — similar to Camunda/Flowable process visualization. This gives stakeholders a familiar process map rather than a raw state machine diagram.

### 12.2 Two Export Modes

**Static (workflow template):**
Generated from the Workflow DSL definition. Shows the complete state machine as a BPMN diagram — all states as tasks/gateways, all transitions as sequence flows. No execution data. Used for documentation and process review.

**Dynamic (execution instance heatmap):**
Generated from a specific `workflow_execution` + `workflow_transition_log`. Overlays the static diagram with:
- Current state (highlighted node)
- Route taken (visited transitions highlighted)
- Heatmap overlay: transition frequency across all instances (color intensity = volume)
- FAULTED states marked with error indicators

This mirrors the Camunda/Flowable instance detail view — stakeholders can see exactly which path a specific loan contract took, and where most contracts drop off across the portfolio.

### 12.3 Export Approach

The BPMN XML is generated server-side from the DSL model:

```
WorkflowDefinition → BpmnExporter → BPMN 2.0 XML
                                         │
                              ┌──────────┴──────────┐
                              ▼                     ▼
                     Static template         Dynamic instance
                     (from DSL only)         (+ transition_log data)
```

States map to BPMN `<userTask>` or `<serviceTask>` elements. Transitions map to `<sequenceFlow>` with conditions. Terminal states map to `<endEvent>`. FAULTED state maps to `<boundaryErrorEvent>`.

### 12.4 Frontend Integration

The admin panel embeds `bpmn-js` for rendering. Two tabs per workflow view:

- **Process Map** — static BPMN from template export
- **Instance View** — dynamic BPMN with route highlight and heatmap overlay for a specific `eventNumber` or aggregate across all instances of a workflow code

API endpoints:

```
GET /api/workflows/{code}/bpmn                    → static BPMN XML
GET /api/workflows/{code}/bpmn/{eventNumber}      → dynamic BPMN XML for one instance
GET /api/workflows/{code}/bpmn/aggregate          → heatmap BPMN XML across all instances
```

---

## 13. Module Structure

```
root/
├── app/                         ← Spring Boot (Java 25)
├── dsl-api/                     ← DSL interfaces (Kotlin)
├── dsl-runtime/                 ← Builder DSL for .kts (Kotlin)
├── dsl-compiler/                ← Gradle tasks: download, compile, validate
├── temporal-core/               ← Workflow + Activity base classes (Java)
├── bpmn-export/                 ← BPMN 2.0 XML generation from DSL model
└── build.gradle.kts

app/src/main/java/
├── api/
│   ├── EventController.java
│   ├── BpmnController.java
│   └── DevDslController.java                  // @Profile("dev")
├── service/
│   ├── EventService.java
│   ├── WorkflowResolver.java
│   ├── WorkflowExecutor.java
│   ├── ContextEvaluator.java
│   ├── ContextEncryptionService.java          // encrypt/decrypt JSONB fields
│   └── DslVersionService.java
├── temporal/
│   ├── EventWorkflow.java
│   ├── EventWorkflowImpl.java
│   ├── TransactionActivity.java
│   └── TransactionActivityImpl.java
├── state/
│   ├── WorkflowExecutionRepository.java
│   ├── EventExecutionRepository.java
│   ├── WorkflowTransitionLogRepository.java
│   └── ExecutionContextImpl.java
└── dsl/
    └── DslLoader.java

dsl-api/src/main/kotlin/
├── WorkflowDefinition.kt
├── TransitionRule.kt
├── EventDefinition.kt
├── TransactionDefinition.kt
├── HelperDefinition.kt
├── ConditionDefinition.kt
├── ContextBlock.kt
├── DisplayBlock.kt
├── FinishBlock.kt
├── context/
│   ├── BaseContext.kt
│   ├── ParameterContext.kt
│   ├── EnrichmentContext.kt
│   ├── TransactionContext.kt
│   └── FinishContext.kt
├── ExecutionResult.kt
├── HelperInput.kt
├── HelperOutput.kt
├── HelperFunction.kt
└── Action.kt

dsl-runtime/src/main/kotlin/
├── WorkflowBuilder.kt
├── EventBuilder.kt
├── TransactionBuilder.kt
├── HelperBuilder.kt
├── ConditionBuilder.kt
├── StubWorkflowGenerator.kt
├── ConditionDsl.kt                            // when/then/orWhen/otherwise
├── StepHandle.kt                              // .then() chaining
└── DslRegistry.kt

dsl-compiler/src/main/kotlin/
├── tasks/
│   ├── DownloadDslTask.kt
│   ├── CompileDslTask.kt
│   └── ValidateDslTask.kt
├── ImportResolver.kt
├── SemanticValidator.kt
└── KtsCompiler.kt

bpmn-export/src/main/java/
├── BpmnExporter.java
├── StaticBpmnGenerator.java
├── DynamicBpmnGenerator.java
└── BpmnHeatmapOverlay.java

temporal-core/src/main/java/
├── BaseTransactionActivity.java
└── WorkflowContextBridge.java
```

---

## 14. Risks & Open Questions

### 14.1 Risk Table

| Risk | Description | Severity | Mitigation |
|---|---|---|---|
| Kotlin in Java shop | Team unfamiliar with .kts | Medium | DSL layer isolated; only dsl-runtime requires Kotlin |
| Temporal worker versioning | In-flight isolation requires careful deployment | High | Spike before v1 |
| Helper idempotency | Temporal retries may cause double side effects | High | Convention in v1; framework enforcement in v2 |
| Compensating transactions | Wrong rollback() breaks accounting ledger | High | Accounting/compliance review gate required |
| Partial compensation failure | Rollback itself fails mid-way | High | Temporal retry on rollback activity; per-tx rollback status |
| JSONB encryption performance | Encrypt/decrypt on every read/write adds latency | Medium | Benchmark in staging; consider field-level vs row-level encryption strategy |
| prolong() loop risk | ctx.prolong() chain with no terminal state is infinite | Medium | Terminal state check before prolong; DSL authors responsible |
| context {} failure UX | Pre-Temporal fault has no Temporal trace | Medium | CONTEXT_FAULT error must be detailed; admin UI shows pre-Temporal faults separately |
| BPMN generation fidelity | Complex DSL conditions may not map cleanly to BPMN elements | Low | BPMN export is for visualization only — not executable |
| Temporal UI iframe CORS (v2) | Iframe embed blocked by Temporal CSP | Low | Use link in v1 |

### 14.2 Resolved Decisions

The following questions from earlier drafts are now resolved:

- **Context inheritance**: Shared across transitions. No fresh seed between states.
- **Preview confirmation flow**: No server-side waiting. Client calls SUBMIT independently when satisfied.
- **Stakeholder funnel UI**: v2. Data model ready in v1.
- **ctx.prolong() depth guard**: None. DSL author's responsibility.
- **compileDsl semantic validation**: Required (not optional). All references validated at build time.

### 14.3 Open Questions for Team Discussion

1. **Temporal worker versioning strategy**: Dedicated task queue per DSL version, or Temporal worker versioning API? Needs a spike before v1.
2. **DSL PR approval process**: Developer review required for all DSL changes, or analyst self-merge after CI semantic validation passes?
3. **JSONB encryption strategy**: Field-level (encrypt each JSONB value separately) or row-level (encrypt entire JSONB blob)? Affects query capability on encrypted fields.
4. **Accounting review gate**: Who from accounting/compliance owns review of all `rollback()` implementations before go-live?
5. **BPMN export scope for v1**: Static template only in v1, or dynamic instance view too?

---

## 15. Out of Scope (v1)

- VSCode extension (server-side VSCode via code-server covers v1 needs)
- Idempotency framework for Helpers
- Annotation processor for typed Helper/Transaction DSL generation (v2)
- Nexus publishing of typed DSL bindings (v2)
- Stakeholder funnel/heatmap UI frontend (v2)
- Temporal UI iframe embed (v2)
- `DRY_RUN`, `COMPENSATE`, `SUSPEND`, `REACTIVATE` actions
- Dynamic BPMN instance heatmap (v1 ships static template only, dynamic is v2)

---

## 16. References

### 16.1 Temporal

- [Temporal Java SDK — Workflow Versioning](https://docs.temporal.io/dev-guide/java/versioning)
- [Temporal Web UI — Self-Hosted Deployment](https://docs.temporal.io/web-ui)
- [Temporal Workflow Determinism & Versioning — Temporal Blog](https://temporal.io/blog/workflow-versioning)

### 16.2 Community

- [Как мы строили оркестрацию бизнес-процессов на Temporal (Habr)](https://habr.com/ru/articles/970730/)
- [Temporal: практика применения в продакшене (Habr)](https://habr.com/ru/articles/966972/)

### 16.3 Architecture & Patterns

- [Saga Pattern & Compensating Transactions — Microsoft Architecture Guide](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Kotlin DSL Design Patterns — JetBrains Blog](https://blog.jetbrains.com/kotlin/2021/10/kotlin-dsl-best-practices/)
- [bpmn-js — BPMN 2.0 Rendering Library](https://bpmn.io/toolkit/bpmn-js/)

---

*Document v0.4 — refined after second business/dev review (20 remarks). Resolved decisions listed in section 14.2. Remaining open questions in 14.3.*
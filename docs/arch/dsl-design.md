# DSL Design Reference (.kts)

← [Back to TDD](../tdd.md)

---

## 4.1 File & Folder Convention

Each event owns a folder. All DSL objects for that event live in the same folder — analogous to a Java package.
The repository also includes a minimal `build.gradle.kts` to run DSL validation and development checks.

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

Mass operation DSL files live in their own folders under `cbs-rules/mass-operations/`. The `.mass.kts` suffix identifies
them to the compiler. All other conventions (one folder per unit, collocated helpers) apply identically.

---

## 4.2 Import System

Each DSL file declares imports. Imports resolve to other DSL files or framework interfaces. Import aliases are supported
with `as`. The `compileDsl` Gradle task validates all imports — missing references fail the build.

> **Implementation note (T34):** The bare `#import` syntax shown below is not valid Kotlin. In the actual
> implementation, imports are written as `// #import` comments. This keeps `.kts` files syntactically valid Kotlin —
> IDEs show no errors, no preprocessor stripping is needed. The `ImportParser` scans for lines matching
> `^\s*//\s*#import\s+(\S+)(?:\s+as\s+(\S+))?`. `framework.*` imports are no-ops (types are already on the classpath
> via `defaultImports`). See `dsl/src/main/kotlin/cbs/dsl/compiler/ImportParser.kt`.

```kotlin
// Import all objects from an event folder
// #import loan-disbursement.* as disb

// Import a specific helper file
// #import global.banking-helpers

// Import framework types (no-op — already on classpath)
// #import framework.ExecutionContext
// #import framework.Action
```

Mass operation DSL files use the same `// #import` syntax, with additional framework imports:

```kotlin
// #import mass-operations.interest-charge.* as ic
// #import loan-disbursement.* as disb
// #import global.banking-helpers
// #import framework.Action
// #import framework.Signal
```

### Two-pass compilation

Because imports reference definitions from *other* files, `DslCompiler` uses a two-pass strategy
(see `dsl/src/main/kotlin/cbs/dsl/compiler/DslCompiler.kt`):

```
Pass 1 — build registry
  Eval all .kts files with no import injection.
  Each file's scope (EventDslScope, TransactionDslScope, HelperDslScope,
  ConditionDslScope, MassOperationDslScope, WorkflowDslScope) is extracted
  and its definitions registered into a merged DslRegistry.

Pass 2 — inject imports
  For each file that contains at least one // #import line:
    1. ImportParser.parse(content)  → List<ImportDirective>
    2. ImportResolver(mergedRegistry).resolve(directives) → Map<String, ImportScope>
    3. ScriptHost.eval(content, fileName, providedImports)
       injects a single top-level property `imports: Map<String, ImportScope>`
    4. Merged registry updated with new definitions (overwrite by code).
```

Script usage after injection:

```kotlin
// #import loan-disbursement.* as disb

val disb = imports["disb"]!!          // ImportScope
val event = disb["LOAN_DISBURSEMENT"] // EventDefinition
```

---

## 4.2.1 Execution Modes

DSL supports two execution modes with a shared contract surface from `dsl-api`.

| Mode      | Environment                          | Source of definitions              | Primary module | Notes                                   |
|-----------|--------------------------------------|------------------------------------|----------------|-----------------------------------------|
| `STRICT`  | production / CI / non-dev backend    | compiled Kotlin classes from `dsl` | `dsl`          | required default mode                   |
| `LENIENT` | development only (`@Profile("dev")`) | raw `.kts` interpreted directly    | `dsl`          | skips compile/package for fast feedback |

### Strict Mode

- Backend loads compiled DSL classes from the `dsl` module at runtime.
- Semantic validation is enforced before runtime usage.

### Lenient Mode

- `DevDslController` uses `DevDslEvaluator` in `dsl` to execute `.kts` without compile/package.
- Intended for local iteration speed and diagnostics.
- Must not be used as production execution path.

### Shared API Contract Strategy

- Both strict and lenient paths bind to the same Java interfaces and records in `dsl-api`
  (`WorkflowDefinition`, `EventDefinition`, `TransactionDefinition`, `MassOperationDefinition`, context types).
- `dsl` implements these contracts in Kotlin; `backend` depends on `dsl-api` directly for type safety.

---

## 4.3 Workflow DSL — Refined Transitions

Transitions are full closures receiving `ctx`. Each transition can run multiple events — async by default (parallel
`CompletablePromise`), or explicitly awaited. `ctx.resumeEvent()` loads the existing workflow context from PostgreSQL
and re-runs `finish {}` and `display {}` without recalculating `context {}` or `transactions {}` — it sets
`ctx.isResumed = true` so DSL authors can branch on it.

`onFault {}` is a closure per transition (replaces the simple `onFault "FAULTED"` string from earlier versions).

`states`, `initial`, and `terminal` are optional. If omitted, states are inferred from transition declarations;
`initial` defaults to the first `from` state; `terminal` defaults to states with no outgoing transitions.

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

| Method                   | context {}     | transactions {} | finish {} | display {} | ctx.isResumed |
|--------------------------|----------------|-----------------|-----------|------------|---------------|
| `ctx.runEvent(event)`    | recalculated   | executed        | executed  | executed   | false         |
| `ctx.resumeEvent(event)` | loaded from PG | skipped         | executed  | executed   | true          |

---

## 4.4 MassOperation DSL

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

---

## 4.5 Event DSL

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

        // Named condition definition (see section 4.10)
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

---

## 4.5.1 MassOperation Item Context

When a mass operation calls an event or workflow per item, the following context variables are automatically injected:

| Variable                   | Description                                         |
|----------------------------|-----------------------------------------------------|
| `ctx["item"]`              | The source row for this item (Map from data source) |
| `ctx.isMassOperation`      | `true` when called from a mass operation            |
| `ctx["massOperationCode"]` | Code of the parent mass operation                   |
| `ctx["businessDate"]`      | From mass operation context block                   |
| `ctx.isResumed`            | `true` when called via `ctx.resumeEvent()`          |

These are available in event/workflow DSL via `when/then/otherwise` for mass-operation-specific branching. Events remain
unaware of whether they are called from a single execution or a mass operation — `ctx.isMassOperation` is the only
signal of that distinction.

---

## 4.6 Transaction DSL

Preview and rollback closures are optional. If omitted, the framework calls the corresponding method on the Spring bean
directly. When declared, `ctx.delegate()` explicitly calls the interface method in addition to the closure body — giving
fine-grained control over whether to extend or replace the bean's default behavior.

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

| Source                      | DSL syntax                                                           |
|-----------------------------|----------------------------------------------------------------------|
| Required parameter          | `ctx["customerId"]`                                                  |
| Optional parameter          | `ctx["accountNumber"] ?: "default"`                                  |
| Pre-evaluated context value | `ctx["customerCode"]` (from `context {}`)                            |
| Earlier transaction output  | `ctx.transactionResult("KYC_CHECK")["verified"]`                     |
| Helper call                 | `ctx.helper("NAME", mapOf("key" to value))`                          |
| Chained helper              | `ctx.helper("OUTER", mapOf("x" to ctx.helper("INNER", mapOf(...))))` |
| Spring bean                 | `ctx.resolve(MyBean::class).myMethod()`                              |
| Current action              | `ctx.action`                                                         |
| Event code                  | `ctx.eventCode`                                                      |
| Event number (PK)           | `ctx.eventNumber`                                                    |
| Workflow state              | `ctx.workflowState`                                                  |
| Workflow instance ID        | `ctx.workflowInstanceId`                                             |
| Call interface method       | `ctx.delegate()`                                                     |

---

## 4.7 Helper Chaining DSL

Helpers are always `HelperFunction<I, O>`. In DSL they are called via `ctx.helper("NAME", mapOf(...))` and can be
chained — output of one helper becomes a value in the `mapOf` of the next.

In v1, keys are strings. In v2, annotation processor generates typed constructors — no string keys, just positional
arguments.

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

---

## 4.8 Helper DSL Files

### Helper code → implementation lookup

When the DSL calls `ctx.helper("LOAN_CONDITIONS_BY_ID", mapOf(...))`, the runtime looks up the registered
`HelperDefinition` whose `code == "LOAN_CONDITIONS_BY_ID"`. In production this resolves to a Spring bean
(or compiled class) annotated/registered with that code. In tests and sample `.kts` files, the definition
is declared inline using the `helper("CODE") { ... }` DSL block.

The optional `name` field on a helper (or transaction) lets you give the DSL override a human-readable
label that distinguishes it from the underlying production bean. This is especially useful in test scenarios
where you want to override a production bean with a test stub:

```kotlin
helpers {
    helper("LOAN_CONDITIONS_BY_ID") {
        name("TestLoanConditionsById")   // identifies this as the test stub
        execute { ctx -> mapOf("loanId" to ctx.params["loanId"], "currency" to "USD") }
    }
}
```

Later, the engine can call: `ctx.helper("TestLoanConditionsById", mapOf(...))` to invoke the named override
directly, bypassing the production bean lookup. The `code` field is always the primary lookup key; `name`
is a secondary label for overrides and control-flow disambiguation.

The same `name` field applies to transactions:

```kotlin
transaction("KYC_CHECK") {
    name("TestKycCheck")   // identifies this as the test override of the KYC_CHECK bean
    execute { ctx -> ctx["kycVerified"] = true }
}
```

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

---

## 4.9 Conditional Transactions & Loops

Conditions use `when/then/orWhen/otherwise` inside a step. A named `Condition` DSL object can be declared separately and
reused.

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

    // Named condition reference (see section 4.10)
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

---

## 4.10 Condition DSL

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

## 4.11 Annotation Processor & Compile-Time Registration (SPI)

### Overview

In production, DSL implementations (transactions, helpers, conditions) are Spring beans or compiled
Kotlin classes. The engine resolves `ctx.helper("LOAN_CONDITIONS_BY_ID", ...)` by looking up the
`HelperDefinition` registered under code `"LOAN_CONDITIONS_BY_ID"` in the `ImplRegistry`.

To avoid manual wiring and runtime classpath scanning, the `@DslComponent` annotation (in `dsl-api`)
marks a class as a DSL implementation. The Java APT annotation processor (`dsl-codegen` module) reads
these annotations at compile time and generates an `ImplRegistrationProvider` implementation that
registers all discovered components via SPI (Service Provider Interface).

At application startup, `SpiImplRegistryLoader` loads all `ImplRegistrationProvider` implementations
via `ServiceLoader` and invokes `register(registry)` on each to populate the `ImplRegistry`. This is
zero-reflection, zero-classpath-scanning — all wiring is resolved at compile time.

### Execution Modes: STRICT vs LENIENT

DSL compilation supports two modes controlled by `app.cbs.dsl.mode` in `application.yml`:

| Mode      | Default | Environment                          | Code Import Resolution                          |
|-----------|---------|--------------------------------------|-------------------------------------------------|
| `STRICT`  | Yes     | Production / CI / non-dev backend    | SPI-registered `ImplRegistry` only (no scanning) |
| `LENIENT` | No      | Development only (`@Profile("dev")`) | Falls back to runtime classpath scanning        |

**STRICT mode (production default):**
- `CodeImportResolver` is disabled
- `// #import code:...` directives resolve from `ImplRegistry.resolveByClassName()` / `resolveByPackagePrefix()`
- Fast startup, deterministic, zero reflection

**LENIENT mode (development):**
- Falls back to `CodeImportResolver` runtime classpath scanning when SPI registry unavailable
- Slower startup but supports ad-hoc `.kts` development without recompilation

### `@DslComponent` annotation

```kotlin
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
class KycCheckTransaction : TransactionDefinition {
    override val code = "KYC_CHECK"
    override fun execute(ctx: TransactionContext) { /* ... */ }
    override fun preview(ctx: TransactionContext) {}
    override fun rollback(ctx: TransactionContext) {}
}
```

The `code` must match the string used in `.kts` files. The `type` tells the processor which registry
map to populate (`TRANSACTION`, `HELPER`, `CONDITION`, `WORKFLOW`, or `MASS_OPERATION`).

### Compile-time code generation (T47)

The Java APT annotation processor generates a `GeneratedImplRegistrations.java` file in the package
`cbs.dsl.codegen.generated`:

```java
// Generated — do not edit
package cbs.dsl.codegen.generated;

import cbs.dsl.api.ImplRegistrationProvider;
import cbs.dsl.api.WritableRegistry;

public class GeneratedImplRegistrations implements ImplRegistrationProvider {

  @Override
  public void register(WritableRegistry registry) {
    registry.register(new KycCheckTransaction());
    registry.register(new LoanConditionsHelper());
    registry.register(new BorrowerAccountReadyCondition());
  }
}
```

The processor also generates an SPI service file
`META-INF/services/cbs.dsl.api.ImplRegistrationProvider` containing the fully-qualified name
`cbs.dsl.codegen.generated.GeneratedImplRegistrations`.

**Validation:** The processor validates each `@DslComponent` annotated class at compile time:
- Must be a class (not interface or object)
- Must have a public no-arg constructor
- Must implement exactly one of: `TransactionDefinition`, `HelperDefinition`, `ConditionDefinition`,
  `WorkflowDefinition`, `MassOperationDefinition`
- `code` attribute must not be blank

### SPI-based loading at startup (T49)

`SpiImplRegistryLoader` (in `dsl` module) loads all providers:

```kotlin
object SpiImplRegistryLoader {
  fun loadInto(registry: ImplRegistry) {
    ServiceLoader.load(ImplRegistrationProvider::class.java).forEach { provider ->
      provider.register(registry)
    }
  }
}
```

`ImplRegistryAutoConfiguration` (in `starter` module) creates the Spring bean:

```kotlin
@AutoConfiguration
class ImplRegistryAutoConfiguration {
  @Bean
  fun implRegistry(): ImplRegistry {
    val registry = ImplRegistry()
    SpiImplRegistryLoader.loadInto(registry)
    return registry
  }
}
```

**Duplicate detection:** If two providers register DSL components with the same `code`, an
`IllegalStateException` is thrown with a clear message identifying the conflicting providers.

### Runtime fallback for .kts files

`ImplRegistry.populateFrom(DslRegistry)` provides a runtime fallback for definitions compiled from
`.kts` files at runtime. It scans and registers them by code and name. Test setups may still
pre-register `TestXxx` instances manually.

### Test impl registration

Test classes annotated with `@DslComponent` (e.g., `TestTransaction`, `TestHelper`, `TestCondition`)
participate in compile-time registration when the `dsl-codegen` processor is applied to the test
source set. The processor generates `GeneratedImplRegistrations` for both main and test classpaths.

### Lookup priority in `ImplRegistry`

| Priority | Lookup Key           | Source                              | Use Case                                    |
|----------|----------------------|-------------------------------------|---------------------------------------------|
| 1        | Name-keyed entry     | `definition.name`                   | Test overrides, named DSL stubs             |
| 2        | Code-keyed entry     | `definition.code`                   | Production beans, compiled `.kts` definitions |
| 3        | Class-name lookup    | `ImplRegistry.resolveByClassName()` | STRICT mode CODE imports                    |

This means a `TestTransaction` registered under `name = "TestKycCheck"` takes precedence over the
production `KycCheckTransaction` registered under `code = "KYC_CHECK"` when the DSL calls
`ctx.helper("TestKycCheck", ...)`. The production bean is still reachable via `"KYC_CHECK"`.

### Module dependencies

```
dsl-api  ←  dsl-codegen (Java APT processor, depends only on dsl-api)
   ↑
  dsl    ←  ImplRegistry implements WritableRegistry (from dsl-api)
   ↑
starter  ←  ImplRegistryAutoConfiguration + Java APT (dsl-codegen) for compile-time registration
```

The `WritableRegistry` interface in `dsl-api` breaks the circular dependency: `dsl-codegen` no longer
depends on `dsl`, only on `dsl-api`.

### Annotation usage examples

**Transaction implementation:**
```kotlin
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
class KycCheckTransaction : TransactionDefinition {
    override val code = "KYC_CHECK"
    override val name: String? get() = "ProductionKycCheck"

    override fun preview(ctx: TransactionContext) {
        // Preview logic
    }

    override fun execute(ctx: TransactionContext) {
        ctx["kycVerified"] = true
    }

    override fun rollback(ctx: TransactionContext) {
        // Rollback logic
    }
}
```

**Helper implementation:**
```kotlin
@DslComponent(code = "LOAN_CONDITIONS_BY_ID", type = DslImplType.HELPER)
class LoanConditionsHelper : HelperDefinition {
    override val code = "LOAN_CONDITIONS_BY_ID"
    override val name: String? get() = "ProductionLoanConditions"

    override fun execute(input: HelperInput): HelperOutput {
        val loanId = (input as MapHelperInput).params["loanId"]
        return AnyHelperOutput(mapOf("loanId" to loanId, "currency" to "USD"))
    }
}
```

**Test implementation (override):**
```kotlin
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
class TestKycCheck : TransactionDefinition {
    override val code = "KYC_CHECK"
    override val name: String? get() = "TestKycCheck"

    override fun execute(ctx: TransactionContext) {
        ctx["kycVerified"] = true
        ctx["_implClass"] = "TestTransaction"  // Marker for test assertions
    }

    override fun preview(ctx: TransactionContext) {}
    override fun rollback(ctx: TransactionContext) {}
}
```

---

## 4.12 JSON-Native Parameters & Avaje Jsonb

### Philosophy

All parameters flowing into and out of DSL components are **JSON-native**. This means:

- Every `XxxInput` and `XxxOutput` type is a plain data structure that serializes cleanly to JSON.
- No non-JSON types (e.g. raw `Map<String, Any>`, `java.sql.ResultSet`, or opaque Spring beans) cross DSL boundaries.
- Parameters are validated and bound via reflection-free generated adapters.

This design guarantees that DSL definitions can be:
- Stored in PostgreSQL as JSONB columns.
- Sent over HTTP or MQ without custom serializers.
- Audited, logged, and replayed from plain JSON snapshots.

### Avaje Jsonb

CBS-Nova uses **Avaje Jsonb** for JSON binding. Unlike Jackson or Gson, Avaje Jsonb is reflection-free: an annotation processor generates `JsonAdapter` source code at compile time. This improves startup time, reduces memory footprint, and eliminates runtime reflection surprises.

**Key characteristics:**

| Trait | Value |
|-------|-------|
| Size | ~200 KB + generated adapters |
| Dependencies | Zero |
| Performance | One of the fastest Java JSON libraries |
| Standard support | Jakarta JSON-B annotations |
| Modern Java | Records, generics, `java.time` supported |

**Dependency Coordinates:**
- `io.avaje:avaje-jsonb:3.11` (Runtime API)
- `io.avaje:avaje-jsonb-generator:3.11` (KAPT Annotation Processor — `dsl-api` uses `kapt` because its test sources are Kotlin)

### Input/Output Types

Every DSL definition interface declares a typed `execute(input)` (or `evaluate(input)`) method. The input and output types are annotated with `@Json` so Avaje can generate adapters:

```kotlin
@Json
 data class TransactionInput(
  val agreementId: String,
  val amount: BigDecimal,
  val currency: String,
  val accountNumber: String?   // nullable = optional parameter
) : DslInput

@Json
 data class TransactionOutput(
  val transactionId: String,
  val status: String
) : DslOutput
```

The same pattern applies to **all** definition kinds:

| Definition | Input Type | Output Type | Method |
|------------|------------|-------------|--------|
| `HelperDefinition` | `HelperInput` | `HelperOutput` | `execute(input)` |
| `TransactionDefinition` | `TransactionInput` | `TransactionOutput` | `execute(input)` |
| `EventDefinition` | `EventInput` | `EventOutput` | `execute(input)` |
| `ConditionDefinition` | `ConditionInput` | `ConditionOutput` | `evaluate(input)` |
| `WorkflowDefinition` | `WorkflowInput` | `WorkflowOutput` | `execute(input)` |
| `MassOperationDefinition` | `MassOperationInput` | `MassOperationOutput` | `execute(input)` |

### Required vs Optional Parameters

Kotlin nullability determines whether a parameter is required or optional:

- **Required:** non-nullable type (`String`, `BigDecimal`, `Long`)
- **Optional:** nullable type (`String?`, `BigDecimal?`) or field with a default value

At compile time, the `dsl-codegen` Java APT processor inspects the `Input` class referenced by each `@DslComponent` and derives `List<ParameterDefinition>` from the class properties. This metadata is registered alongside the component so the engine can validate parameter presence before execution.

```kotlin
@Json
 data class KycCheckInput(
  val customerId: String,        // required
  val documentType: String?      // optional
) : TransactionInput

@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
class KycCheckTransaction : TransactionDefinition {
    override val code = "KYC_CHECK"

    override fun execute(input: KycCheckInput): TransactionOutput {
        // input.customerId is guaranteed non-null
        // input.documentType may be null
        return TransactionOutput(verified = true)
    }

    override fun preview(input: KycCheckInput): TransactionOutput { ... }
    override fun rollback(input: KycCheckInput): TransactionOutput { ... }
}
```

### Module Dependencies

```
dsl-api  ←  avaje-jsonb (API only, no runtime reflection)
   ↑
dsl-codegen  ←  inspects @Json classes, generates ParameterDefinition metadata
   ↑
  dsl    ←  runtime uses Jsonb.builder() to adapt Map<String, Any?> → typed Input
   ↑
starter  ←  provides Jsonb bean
```

### Migration Notes

- `MapHelperInput` and `AnyHelperOutput` are deprecated in favor of typed `@Json` classes.
- `HelperFunction<I, O>` is removed; `HelperDefinition` directly declares `fun execute(input: HelperInput): HelperOutput`.
- Builders in `dsl/runtime/` continue to work but wrap raw `Map<String, Any?>` into the typed `Input` via Avaje before invoking `execute`.

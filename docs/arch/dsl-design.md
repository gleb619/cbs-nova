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

```kotlin
// Import all objects from an event folder
#import loan-disbursement.* as disb

// Import a specific helper file
#import global.banking-helpers

// Import framework types
#import framework.ExecutionContext
#import framework.Action
```

Mass operation DSL files use the same `#import` syntax, with additional framework imports:

```kotlin
#import mass-operations.interest-charge.* as ic
#import loan-disbursement.* as disb
#import global.banking-helpers
#import framework.Action
#import framework.Signal
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

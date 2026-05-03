# DSL Design Reference (Java)

← [Back to TDD](../tdd.md)

---

## 4.1 File & Folder Convention

Each event owns a folder. All DSL objects for that event live in the same folder — analogous to a Java package.
The repository also includes a minimal `build.gradle` to run DSL validation and development checks.

```
cbs-rules/
├── global/
│   └── BankingHelpers.helper.java          ← available to all events
│
├── loan-disbursement/
│   ├── LoanDisbursementEvent.java
│   ├── DebitFundingAccountTransaction.java
│   ├── CreditBorrowerAccountTransaction.java
│   ├── PostDisbursementEntryTransaction.java
│   └── LoanHelpers.helper.java             ← scoped to this event only
│
├── loan-onboarding/
│   ├── LoanOnboardingEvent.java
│   ├── KycCheckTransaction.java
│   ├── CreditScoringTransaction.java
│   └── ...
│
├── LoanContractWorkflow.java               ← workflow at root, references event folders
│
└── mass-operations/
    ├── interest-charge/
    │   ├── InterestChargeMassOperation.java
    │   └── InterestChargeHelpers.helper.java
    ├── penalty-accrual/
    │   └── PenaltyAccrualMassOperation.java
    └── government-upload/
        └── GovernmentUploadMassOperation.java
```

Mass operation DSL files live in their own folders under `cbs-rules/mass-operations/`. The `.mass.java` suffix (or
`MassOperation` class name convention) identifies them to the compiler. All other conventions (one folder per unit,
collocated helpers) apply identically.

---

## 4.2 Import System

Each DSL file declares regular Java imports. Imports resolve to other DSL files or framework interfaces. Import
aliases are not supported — use standard Java `import` statements.

```java
// Import all objects from an event folder (package)
import loan.disbursement.*;

// Import a specific helper file
import global.BankingHelpers;

// Import framework types
import cbs.dsl.api.ExecutionContext;
import cbs.dsl.api.Action;
```

Mass operation DSL files use the same standard Java `import` syntax, with additional framework imports:

```java
import mass.operations.interest.charge.*;
import loan.disbursement.*;
import global.BankingHelpers;
import cbs.dsl.api.Action;
import cbs.dsl.api.Signal;
```

### Two-pass compilation

Because imports reference definitions from *other* files, `DslCompiler` uses a two-pass strategy:

```
Pass 1 — build registry
  Parse all .java DSL files with no import resolution.
  Each file's annotations and fluent builder calls are extracted
  and its definitions registered into a merged DslRegistry.

Pass 2 — resolve imports
  For each file that imports from other DSL packages:
    1. Resolve all import declarations against the merged DslRegistry
    2. Validate that all referenced events, helpers, transactions, conditions exist
    3. Produce a validated, merged registry
```

---

## 4.2.1 Execution Modes

DSL supports two execution modes with a shared contract surface from `dsl-api`.

| Mode        | Environment                          | Source of definitions              | Primary module | Notes                                   |
|-------------|--------------------------------------|------------------------------------|----------------|-----------------------------------------|
| `GENERATED` | production / CI / non-dev backend    | compiled generated Java classes    | `dsl-codegen`  | required default mode                   |
| `REFLECTED` | development only (`@Profile("dev")`) | raw `.java` interpreted via reflection | `dsl`        | skips compile/package for fast feedback |

### Generated Mode (Production)

- **Layer 1:** The `dsl-codegen` annotation processor reads `@DslComponent` annotated `*Function` classes at compile time and generates `*Definition` wrappers + SPI registration.
- **Layer 2:** The `DslCompiler` parses `.java` DSL files (events, workflows, mass operations) and generates `*Definition` implementations.
- **Layer 3:** `dsl-codegen` reads all compiled `*Definition` classes and generates Temporal `Workflow` and `Activity` implementations in `generated-sources/`.
- Backend loads compiled generated classes at runtime.
- Semantic validation is enforced before runtime usage.

### Reflected Mode (Development)

- `DevDslController` uses `DevDslEvaluator` in `dsl` to execute `.java` DSL definitions via reflection without Layer 3 code generation.
- A generic `ReflectiveWorkflow` and `ReflectiveActivity` wrapper interprets `*Definition` metadata at runtime.
- `@DslComponent` `*Function` classes are still processed through Layer 1 (their `*Definition` wrappers are generated and registered).
- Intended for local iteration speed and diagnostics.
- Must not be used as production execution path.

### Shared API Contract Strategy

- Both generated and reflected paths bind to the same Java interfaces and records in `dsl-api`
  (`WorkflowDefinition`, `EventDefinition`, `TransactionDefinition`, `HelperDefinition`, `ConditionDefinition`, `MassOperationDefinition`, context types).
- `dsl` implements these contracts in Java; `backend` depends on `dsl-api` directly for type safety.

---

## 4.3 Workflow DSL — Refined Transitions

Transitions are full closures receiving `ctx`. Each transition can run multiple events — async by default (parallel
`CompletablePromise`), or explicitly awaited. `ctx.resumeEvent()` loads the existing workflow context from PostgreSQL
and re-runs `finish {}` and `display {}` without recalculating `context {}` or `transactions {}` — it sets
`ctx.isResumed = true` so DSL authors can branch on it.

`onFault {}` is a closure per transition (replaces the simple `onFault "FAULTED"` string from earlier versions).

`states`, `initial`, and `terminal` are optional. If omitted, states are inferred from transition declarations;
`initial` defaults to the first `from` state; `terminal` defaults to states with no outgoing transitions.

```java
// LoanContractWorkflow.java
package workflow;

import loan.onboarding.*;
import loan.disbursement.*;
import loan.cancellation.*;
import loan.closure.*;
import global.BankingHelpers;
import cbs.dsl.api.Action;
import cbs.dsl.api.WorkflowDsl;

public class LoanContractWorkflow {

    public static void define(WorkflowDsl dsl) {
        dsl.workflow("LOAN_CONTRACT")
            .states("DRAFT", "ENTERED", "ACTIVE", "CANCELLED", "CLOSED", "FAULTED")
            .initial("ENTERED")
            .terminal("CLOSED", "CANCELLED")

            .transition("DRAFT", "ENTERED", Action.SUBMIT, ctx -> {
                // Multiple events per transition — async by default
                var agreement = ctx.runEvent("LOAN_CREATE_AGREEMENT");
                var notify = ctx.runEvent("LOAN_ONBOARDING_NOTIFICATION");
                ctx.await(agreement, notify);     // explicit barrier if needed
            })
            .onFault(ctx -> {
                ctx.setStatus("FAULTED");
                ctx.runEvent("LOAN_FAULT_NOTIFICATION");
            })

            .transition("ENTERED", "ACTIVE", Action.APPROVE, ctx -> {
                // resumeEvent: loads saved context from PG, re-runs finish/display only.
                // ctx.isResumed = true inside the event DSL for branching.
                ctx.resumeEvent("LOAN_DISBURSEMENT");
            })
            .onFault(ctx -> {
                ctx.setStatus("FAULTED");
            })

            .transition("ENTERED", "CANCELLED", Action.CANCEL, ctx -> {
                ctx.runEvent("LOAN_CANCELLATION");
            })
            .onFault(ctx -> {
                ctx.setStatus("FAULTED");
            })

            .transition("ENTERED", "ENTERED", Action.REJECT, ctx -> {
                ctx.runEvent("LOAN_REJECTION_NOTICE");
            })

            .transition("ACTIVE", "CLOSED", Action.CLOSE, ctx -> {
                ctx.runEvent("LOAN_CLOSURE");
            })
            .onFault(ctx -> {
                ctx.setStatus("FAULTED");
            })

            .transition("ACTIVE", "CANCELLED", Action.CANCEL, ctx -> {
                ctx.runEvent("LOAN_EARLY_TERMINATION");
            })
            .onFault(ctx -> {
                ctx.setStatus("FAULTED");
            })

            .transition("FAULTED", "ENTERED", Action.ROLLBACK, ctx -> {
                ctx.runEvent("LOAN_FAULT_COMPENSATION");
            });
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

```java
// mass-operations/interest-charge/InterestChargeMassOperation.java
package mass.operations.interest.charge;

import global.BankingHelpers;
import cbs.dsl.api.Action;
import cbs.dsl.api.Signal;
import cbs.dsl.api.MassOperationDsl;

public class InterestChargeMassOperation {

    public static void define(MassOperationDsl dsl) {
        dsl.massOperation("INTEREST_CHARGE")
            .category("CREDITS")

            // --- Triggers (one or more, at least one required) ---
            .cron("0 1 * * *")
            .once("2025-12-31T23:59:00")
            .onSignal(Signal.external("INTEREST_CHARGE_TRIGGER"))
            .onSignal(Signal.from("PENALTY_ACCRUAL", Signal.COMPLETED))
            .onSignal(Signal.from("PENALTY_ACCRUAL", Signal.PARTIAL))

            // --- Shared context for all items (evaluated once before processing starts) ---
            .context(ctx -> {
                ctx.put("businessDate", ctx.getOrDefault("date",
                    ctx.helper("CURRENT_BUSINESS_DATE", Map.of())));
                ctx.put("interestRates", ctx.helper("LOAD_INTEREST_RATE_TABLE", Map.of(
                    "date", ctx.get("businessDate")
                )));
            })

            // --- Data source: returns a collection of items to process ---
            .source(ctx -> ctx.helper("SQL_CLIENT", Map.of(
                "QUERY", """
                    SELECT agreement_id, customer_id, outstanding_balance, currency
                    FROM credit_agreements
                    WHERE status = 'ACTIVE' AND next_interest_date <= :businessDate
                    """,
                "PARAMS", Map.of("businessDate", ctx.get("businessDate"))
            )))

            // --- Business lock: prevents concurrent runs ---
            .lock(ctx -> {
                var running = (Long) ctx.helper("SQL_CLIENT", Map.of(
                    "QUERY", """
                        SELECT COUNT(*) FROM mass_operation_execution
                        WHERE code = 'INTEREST_CHARGE'
                          AND status = 'RUNNING'
                          AND started_at > NOW() - INTERVAL '24 hours'
                        """,
                    "PARAMS", Map.of()
                ));
                return running == 0L;   // true = allowed to start, false = locked
            })

            // --- Per-item execution ---
            .item(ctx -> {
                // ctx.get("item") contains the current source row
                var agreementId = (String) ((Map<?,?>) ctx.get("item")).get("agreement_id");

                // Decide: run a workflow transition or a pure event
                if (Boolean.TRUE.equals(((Map<?,?>) ctx.get("item")).get("has_workflow"))) {
                    ctx.runWorkflow(
                        "LOAN_CONTRACT",
                        Action.APPROVE,
                        (Long) ((Map<?,?>) ctx.get("item")).get("event_number"),
                        Map.of(
                            "agreementId", agreementId,
                            "businessDate", ctx.get("businessDate"),
                            "rate", ((Map<?,?>) ctx.get("interestRates")).get(
                                ((Map<?,?>) ctx.get("item")).get("currency"))
                        )
                    );
                } else {
                    ctx.runEvent("INTEREST_CHARGE_EVENT", Map.of(
                        "agreementId", agreementId,
                        "businessDate", ctx.get("businessDate"),
                        "rate", ((Map<?,?>) ctx.get("interestRates")).get(
                            ((Map<?,?>) ctx.get("item")).get("currency"))
                    ));
                }
            })

            // --- Signals emitted during execution ---
            .partial(1000, ctx -> {
                ctx.put("processedSoFar", ctx.processedCount());
                ctx.put("failedSoFar", ctx.failedCount());
            })

            .completed(ctx -> {
                ctx.put("totalProcessed", ctx.processedCount());
                ctx.put("totalFailed", ctx.failedCount());
                ctx.put("businessDate", ctx.get("businessDate"));
            })

            // --- Post-execution hook (runs after all items, success or failure) ---
            .finish((ctx, ex) -> {
                if (ctx.failedCount() > 0) {
                    ctx.helper("SEND_BATCH_FAILURE_REPORT", Map.of(
                        "operation", "INTEREST_CHARGE",
                        "failed", ctx.failedCount(),
                        "total", ctx.processedCount(),
                        "date", ctx.get("businessDate")
                    ));
                }
            });
    }
}
```

---

## 4.5 Event DSL

```java
// loan-disbursement/LoanDisbursementEvent.java
package loan.disbursement;

import global.BankingHelpers;
import cbs.dsl.api.EventDsl;

public class LoanDisbursementEvent {

    public static void define(EventDsl dsl) {
        dsl.event("LOAN_DISBURSEMENT")
            .requiredParam("customerId")
            .requiredParam("loanId")
            .requiredParam("amount")
            .optionalParam("accountNumber")

            // Pre-Temporal enrichment. ctx already has all parameters.
            // Faults immediately if any line throws.
            .context(ctx -> {
                ctx.put("customerCode", ctx.helper("FIND_CUSTOMER_CODE_BY_ID",
                    Map.of("id", ctx.get("customerId"))));
                ctx.put("accountCode", ctx.getOrDefault("accountNumber", "nil"));
                ctx.put("loanConditions", ctx.helper("LOAN_CONDITIONS_BY_ID",
                    Map.of("loanId", ctx.get("loanId"))));
            })

            // Optional. If omitted, everything in context is shown.
            .display(ctx -> {
                ctx.label("Customer ID", ctx.get("customerId"));
                ctx.label("Loan ID", ctx.get("loanId"));
                ctx.label("Amount", ctx.get("amount"));
                ctx.label("Account", ctx.get("accountCode"));
            })

            .transactions(ctx -> {
                // All steps launch as Temporal CompletablePromises immediately.
                // .then() chains a step that starts after its predecessor completes.
                // await() is an explicit barrier for a group.

                var compliance = ctx.step("KYC_CHECK")
                    .then("BLACKLIST_CHECK");   // starts after KYC completes

                var scoring = ctx.step("CREDIT_SCORING");

                ctx.await(compliance, scoring);              // wait for both chains

                // Conditional step — when/then/otherwise
                var debit = ctx.stepWhen(ctx.get("loanConditions") != null)
                    .then("DEBIT_FUNDING_ACCOUNT")
                    .otherwise("DEBIT_FALLBACK_ACCOUNT");

                // Named condition reference (see section 4.10)
                var credit = ctx.stepWhen(ctx.condition("BORROWER_ACCOUNT_READY"))
                    .then("CREDIT_BORROWER_ACCOUNT");

                ctx.await(debit);
                ctx.await(credit);

                var posting = ctx.step("POST_DISBURSEMENT_ENTRY");
                ctx.await(posting);

                // Passing variables into a step
                ctx.step("NOTIFICATION", Map.of("channel", "SMS"));

                // display inside transactions — controls what this step shows in UI
                ctx.displayStep(step -> {
                    step.label("Debit TX", ctx.transactionResult("DEBIT_FUNDING_ACCOUNT").get("txId"));
                    step.label("Credit TX", ctx.transactionResult("CREDIT_BORROWER_ACCOUNT").get("txId"));
                });
            })

            // Runs on both success and failure. ex is null on success.
            .finish((ctx, ex) -> {
                if (ex != null) {
                    ctx.helper("SEND_FAULT_NOTIFICATION", Map.of(
                        "customerId", ctx.get("customerId"),
                        "error", ex.getMessage()
                    ));
                } else {
                    ctx.helper("SEND_DISBURSEMENT_NOTIFICATION", Map.of(
                        "customerId", ctx.get("customerId"),
                        "amount", ctx.get("amount")
                    ));
                    // Triggers next workflow transition internally, no external API call
                    ctx.prolong(Action.APPROVE);
                }
            });
    }
}
```

---

## 4.5.1 MassOperation Item Context

When a mass operation calls an event or workflow per item, the following context variables are automatically injected:

| Variable                   | Description                                         |
|----------------------------|-----------------------------------------------------|
| `ctx.get("item")`          | The source row for this item (Map from data source) |
| `ctx.isMassOperation()`    | `true` when called from a mass operation            |
| `ctx.get("massOperationCode")` | Code of the parent mass operation               |
| `ctx.get("businessDate")`  | From mass operation context block                   |
| `ctx.isResumed()`          | `true` when called via `ctx.resumeEvent()`          |

These are available in event/workflow DSL via `when/then/otherwise` for mass-operation-specific branching. Events remain
unaware of whether they are called from a single execution or a mass operation — `ctx.isMassOperation()` is the only
signal of that distinction.

---

## 4.6 Transaction DSL

Transactions are units of work with `preview()`, `execute()`, and `rollback()` phases.

**Code-based transactions** (recommended for reusable business logic) are Java classes implementing
`TransactionFunction<I extends TransactionArg, O extends TransactionResult>` and annotated with `@DslComponent`.
The Layer 1 annotation processor generates a `TransactionDefinition` wrapper and registers it via SPI.

```java
@DslComponent(code = "DEBIT_FUNDING_ACCOUNT", type = DslImplType.TRANSACTION)
public class DebitFundingAccountTransaction
    implements TransactionFunction<DebitInput, DebitOutput> {

    @Override public DebitOutput preview(DebitInput input) {
        return new DebitOutput(Map.of("description", "Will debit " + input.accountCode()));
    }

    @Override public DebitOutput execute(DebitInput input) {
        var result = AppContext.resolve(DebitFundingAccountService.class)
            .debit(input.accountCode(), input.amount(), input.currency());
        return new DebitOutput(result);
    }

    @Override public DebitOutput rollback(DebitInput input) {
        AppContext.resolve(DebitFundingAccountService.class)
            .postCompensatingEntry(input.txId(), true);
        return new DebitOutput(Map.of("compensated", true));
    }
}
```

**DSL inline transactions** (for event-scoped or ad-hoc rules in `cbs-rules`) use the builder syntax inside a
`.java` DSL file. Preview and rollback closures are optional. If omitted, the framework delegates to the
registered `TransactionDefinition` directly. When declared, `ctx.delegate()` explicitly calls the interface
method in addition to the closure body — giving fine-grained control over whether to extend or replace the
bean's default behavior.

```java
// loan-disbursement/DebitFundingAccountTransaction.java
package loan.disbursement;

import global.BankingHelpers;
import cbs.dsl.api.TransactionDsl;
import cbs.dsl.api.ExecutionResult;

public class DebitFundingAccountTransaction {

    public static void define(TransactionDsl dsl) {
        dsl.transaction("DEBIT_FUNDING_ACCOUNT")

            // Optional. If omitted, calls DebitFundingAccountTransaction.preview()
            .preview(ctx -> {
                var account = (Map<?,?>) ctx.helper("FIND_BANK_ACCOUNT",
                    Map.of("iban", ctx.get("accountCode")));
                return ExecutionResult.success("DEBIT_FUNDING_ACCOUNT", Map.of(
                    "description", "Will debit " + account.get("iban"),
                    "amount", ctx.get("amount"),
                    "currency", ((Map<?,?>) ctx.get("loanConditions")).get("currency")
                ));
            })

            .execute(ctx -> {
                // ctx.get("...") reads from required/optional parameters
                var amount = ctx.get("amount");
                var currency = ((Map<?,?>) ctx.get("loanConditions")).get("currency");

                // Reading output from an earlier transaction in same event
                var kycVerified = ctx.transactionResult("KYC_CHECK").get("verified");

                // Resolve Spring bean directly — no field injection in DSL
                var result = ctx.resolve(DebitFundingAccountService.class)
                    .debit((String) ctx.get("accountCode"), amount, (String) currency);

                ctx.put("debitTxId", result.get("transactionId"));

                return ExecutionResult.success("DEBIT_FUNDING_ACCOUNT", result);
            })

            // Optional. If omitted, calls DebitFundingAccountTransaction.rollback()
            .rollback(ctx -> {
                // ctx.delegate() calls the interface method first, then continues below.
                // Omit ctx.delegate() to fully replace the interface method.
                ctx.delegate();

                // Additional compensating logic on top of the default implementation
                ctx.resolve(DebitFundingAccountService.class)
                    .postCompensatingEntry(
                        (String) ctx.get("debitTxId"),
                        true   // swapAccounts
                    );
            });
    }
}
```

**Reading values in DSL — reference table:**

| Source                      | DSL syntax                                                           |
|-----------------------------|----------------------------------------------------------------------|
| Required parameter          | `ctx.get("customerId")`                                              |
| Optional parameter          | `ctx.getOrDefault("accountNumber", "default")`                       |
| Pre-evaluated context value | `ctx.get("customerCode")` (from `context {}`)                        |
| Earlier transaction output  | `ctx.transactionResult("KYC_CHECK").get("verified")`                 |
| Helper call                 | `ctx.helper("NAME", Map.of("key", value))`                           |
| Chained helper              | `ctx.helper("OUTER", Map.of("x", ctx.helper("INNER", Map.of(...))))` |
| Spring bean                 | `ctx.resolve(MyBean.class).myMethod()`                               |
| Current action              | `ctx.action()`                                                       |
| Event code                  | `ctx.eventCode()`                                                    |
| Event number (PK)           | `ctx.eventNumber()`                                                  |
| Workflow state              | `ctx.workflowState()`                                                |
| Workflow instance ID        | `ctx.workflowInstanceId()`                                           |
| Call interface method       | `ctx.delegate()`                                                     |

---

## 4.7 Helper Chaining DSL

Helpers are always `HelperFunction<I, O>`. In DSL they are called via `ctx.helper("NAME", Map.of(...))` and can be
chained — output of one helper becomes a value in the `Map.of` of the next.

In v1, keys are strings. In v2, annotation processor generates typed constructors — no string keys, just positional
arguments.

```java
// Chained helper call inside a transaction or context block
var accountName = ctx.helper("ACCOUNT_BY_TYPE_CURRENCY", Map.of(
    "ACCOUNT_TYPE_CODE", "INTERNAL",
    "CURRENCY_CODE", ctx.helper("CURRENCY_BY_AGREEMENT", Map.of(
        "agreementId", ctx.get("agreementId")
    )),
    "CUSTOMER_CODE", ctx.transactionResult("TR_ONBOARDING").get("customerCode")
));

ctx.put("fundingAccount", accountName);

// v2 (after annotation processor generates typed bindings):
// var accountName = ctx.helper(new AccountByTypeCurrency(
//     "INTERNAL",
//     ctx.helper(new CurrencyByAgreement(ctx.get("agreementId"))),
//     ctx.transactionResult("TR_ONBOARDING").get("customerCode")
// ));
```

---

## 4.8 Helper DSL Files

**Code-based helpers** (recommended for reusable business logic) are Java classes implementing
`HelperFunction<I extends HelperArg, O extends HelperResult>` and annotated with `@DslComponent`.
The Layer 1 annotation processor generates a `HelperDefinition` wrapper and registers it via SPI.

```java
@DslComponent(code = "LOAN_CONDITIONS_BY_ID", type = DslImplType.HELPER)
public class LoanConditionsHelper
    implements HelperFunction<LoanConditionsInput, LoanConditionsOutput> {

    @Override public LoanConditionsOutput execute(LoanConditionsInput input) {
        var loanId = input.loanId();
        return new LoanConditionsOutput(Map.of("loanId", loanId, "currency", "USD"));
    }
}
```

### Helper code → implementation lookup

When the DSL calls `ctx.helper("LOAN_CONDITIONS_BY_ID", Map.of(...))`, the runtime looks up the registered
`HelperDefinition` whose `code == "LOAN_CONDITIONS_BY_ID"`. In production this resolves to a Spring bean
(or generated class) annotated/registered with that code. In tests and sample `.java` files, the definition
is declared inline using the `helper("CODE") { ... }` DSL block.

The optional `name` field on a helper (or transaction) lets you give the DSL override a human-readable
label that distinguishes it from the underlying production bean. This is especially useful in test scenarios
where you want to override a production bean with a test stub:

```java
dsl.helpers()
    .helper("LOAN_CONDITIONS_BY_ID")
    .name("TestLoanConditionsById")   // identifies this as the test stub
    .execute(ctx -> Map.of("loanId", ctx.params().get("loanId"), "currency", "USD"));
```

Later, the engine can call: `ctx.helper("TestLoanConditionsById", Map.of(...))` to invoke the named override
directly, bypassing the production bean lookup. The `code` field is always the primary lookup key; `name`
is a secondary label for overrides and control-flow disambiguation.

The same `name` field applies to transactions:

```java
dsl.transaction("KYC_CHECK")
    .name("TestKycCheck")   // identifies this as the test override of the KYC_CHECK bean
    .execute(ctx -> ctx.put("kycVerified", true));
```

```java
// loan-disbursement/LoanHelpers.helper.java
package loan.disbursement;

import cbs.dsl.api.HelperDsl;

public class LoanHelpers {

    public static void define(HelperDsl dsl) {
        dsl.helpers()
            // Inline helper using SQL_CLIENT helper (no ctx.db.query — use helper instead)
            .helper("LOAN_CONDITIONS_BY_ID", ctx -> {
                var myLoanId = ctx.params().get("loanId");
                return ctx.helper("CURRENCY_BY_AGREEMENT", Map.of(
                    "agreementId", ctx.params().get("agreementId"),
                    "userLoanId", myLoanId,
                    "amount", ctx.resolve(AccountService.class).calculateAmount(
                        ctx.helper("SQL_CLIENT", Map.of(
                            "QUERY", "SELECT * FROM loan_conditions WHERE loan_id = :loanId",
                            "PARAMS", Map.of("loanId", myLoanId)
                        ))
                    )
                ));
                // Last expression is implicitly returned
            })

            // Inline HTTP helper
            .helper("KYC_STATUS_BY_CUSTOMER", ctx ->
                ctx.helper("HTTP_GET", Map.of(
                    "url", "https://kyc-service/api/status/" + ctx.params().get("customerId")
                ))
            );
    }
}
```

```java
// global/BankingHelpers.helper.java
package global;

import banking.AccountRepository;
import banking.LoanProductRepository;
import banking.CustomerRepository;
import cbs.dsl.api.HelperDsl;

public class BankingHelpers {

    public static void define(HelperDsl dsl) {
        dsl.helpers()
            // Reference existing Spring beans — same declaration syntax as local helpers
            // The only difference: these are available to all events
            .helper("FIND_BANK_ACCOUNT", ctx ->
                ctx.resolve(AccountRepository.class).findByIban((String) ctx.params().get("iban"))
            )

            .helper("FIND_CUSTOMER_CODE_BY_ID", ctx ->
                ctx.resolve(CustomerRepository.class).findCodeById((String) ctx.params().get("id"))
            )

            .helper("CURRENCY_BY_AGREEMENT", ctx ->
                ctx.resolve(LoanProductRepository.class)
                    .findCurrencyByAgreement((String) ctx.params().get("agreementId"))
            );
    }
}
```

---

## 4.9 Conditional Transactions & Loops

Conditions use `when/then/orWhen/otherwise` inside a step. A named `Condition` DSL object can be declared separately and
reused.

```java
.transactions(ctx -> {

    // Inline condition
    var debit = ctx.stepWhen(ctx.get("loanConditions") != null)
        .then("DEBIT_FUNDING_ACCOUNT")
        .otherwise("DEBIT_FALLBACK_ACCOUNT");

    // Named condition reference (see section 4.10)
    var credit = ctx.stepWhen(ctx.condition("BORROWER_ACCOUNT_READY"))
        .then("CREDIT_BORROWER_ACCOUNT");

    // Multi-branch
    var routing = ctx.stepWhen((Long) ctx.get("amount") > 10_000_000L)
        .then("HIGH_VALUE_DEBIT")
        .orWhen("USD".equals(ctx.get("currency")))
        .then("FOREIGN_CURRENCY_DEBIT")
        .otherwise("STANDARD_DEBIT");

    ctx.await(debit, credit, routing);

    // Loop — dynamic transaction list from a helper/SQL result
    var trCodes = (List<String>) ctx.helper("SQL_CLIENT", Map.of(
        "QUERY", "SELECT code FROM pending_transactions WHERE event_id = :id",
        "PARAMS", Map.of("id", ctx.eventNumber())
    ));

    var dynamicSteps = trCodes.stream().map(trCode -> {
        ctx.println("Scheduling transaction: " + trCode);
        return ctx.step(trCode);
    }).toList();

    // Await all dynamic steps
    ctx.await(dynamicSteps.toArray(new StepHandle[0]));
})
```

---

## 4.10 Condition DSL

Named conditions are declared as standalone DSL objects and referenced in `when { ctx.condition(...) }`.

**Code-based conditions** (recommended for reusable business logic) are Java classes implementing
`ConditionFunction<I extends ConditionArg, O extends ConditionResult>` and annotated with `@DslComponent`.
The Layer 1 annotation processor generates a `ConditionDefinition` wrapper and registers it via SPI.

```java
@DslComponent(code = "BORROWER_ACCOUNT_READY", type = DslImplType.CONDITION)
public class BorrowerAccountReadyCondition
    implements ConditionFunction<BorrowerAccountInput, BorrowerAccountOutput> {

    @Override public BorrowerAccountOutput evaluate(BorrowerAccountInput input) {
        var account = (Map<?,?>) ctx.helper("FIND_BANK_ACCOUNT",
            Map.of("iban", input.accountCode()));
        return new BorrowerAccountOutput(account != null && "ACTIVE".equals(account.get("status")));
    }
}
```

**DSL inline conditions** (for event-scoped or ad-hoc rules in `cbs-rules`) use the builder syntax:

```java
// loan-disbursement/BorrowerAccountReadyCondition.java
package loan.disbursement;

import global.BankingHelpers;
import cbs.dsl.api.ConditionDsl;

public class BorrowerAccountReadyCondition {

    public static void define(ConditionDsl dsl) {
        dsl.condition("BORROWER_ACCOUNT_READY", ctx -> {
            var account = (Map<?,?>) ctx.helper("FIND_BANK_ACCOUNT",
                Map.of("iban", ctx.get("accountCode")));
            return account != null && "ACTIVE".equals(account.get("status"));
        });
    }
}
```

---

## 4.11 Code Generation Pipeline (3 Layers)

CBS-Nova uses a three-layer compile-time code generation pipeline. Each layer consumes artifacts from the layer above and produces richer runtime metadata.

#### Layer 1 — Function → Definition (`@DslComponent` processor)

**User input:** Java classes implementing `TransactionFunction`, `HelperFunction`, or `ConditionFunction`, annotated with `@DslComponent`.

```java
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
public class KycCheckTransaction implements TransactionFunction<KycCheckInput, KycCheckOutput> {
    @Override public KycCheckOutput preview(KycCheckInput input) { ... }
    @Override public KycCheckOutput execute(KycCheckInput input) { ... }
    @Override public KycCheckOutput rollback(KycCheckInput input) { ... }
}
```

**Validation:** The processor validates each `@DslComponent` annotated class at compile time:
- Must be a class (not interface)
- Must have a public no-arg constructor
- Must implement exactly one of: `TransactionFunction`, `HelperFunction`, `ConditionFunction`
- `code` attribute must not be blank

**Generated output:** A `*Definition` implementation that wraps the function and carries metadata (code, name, parameters). The processor also generates an SPI registration file.

```java
// Generated — do not edit
package cbs.dsl.codegen.generated.definitions;

public class KycCheckTransactionDefinition implements TransactionDefinition {
    private final KycCheckTransaction function = new KycCheckTransaction();

    @Override public String getCode() { return "KYC_CHECK"; }
    @Override public List<ParameterDefinition> getParameters() { ... }
    @Override public TransactionOutput preview(TransactionInput input) {
        KycCheckInput typed = map(input);
        KycCheckOutput out = function.preview(typed);
        return map(out);
    }
    // ... execute, rollback ...
}
```

```java
// Generated — do not edit
package cbs.dsl.codegen.generated;

public class GeneratedImplRegistrations implements ImplRegistrationProvider {
    @Override public void register(WritableRegistry registry) {
        registry.register(new KycCheckTransactionDefinition());
    }
}
```

`@DslComponent` is **only** valid on:
- `TransactionFunction<I extends TransactionArg, O extends TransactionResult>`
- `HelperFunction<I extends HelperArg, O extends HelperResult>`
- `ConditionFunction<I extends ConditionArg, O extends ConditionResult>`

Events, workflows, and mass operations are **not** annotated with `@DslComponent`. They are defined in `.java` DSL files and processed by Layer 2.

#### Layer 2 — DSL File → Definition (`DslCompiler`)

**User input:** `.java` DSL files in `cbs-rules` repository.

```java
// loan-disbursement/LoanDisbursementEvent.java
public class LoanDisbursementEvent {
    public static void define(EventDsl dsl) {
        dsl.event("LOAN_DISBURSEMENT")
            .requiredParam("customerId")
            .transactions(ctx -> { ... })
            .finish((ctx, ex) -> { ... });
    }
}
```

**Compilation passes:**

```
Pass 1 — Build merged DslRegistry
  Parse all .java DSL files. Extract annotations and fluent builder calls.
  Register definitions into a merged DslRegistry (no import resolution).

Pass 2 — Resolve imports & validate
  Resolve Java imports against the merged registry.
  Semantic validation: all referenced events, helpers, transactions, conditions exist.
```

**Generated output:** `EventDefinition`, `WorkflowDefinition`, and `MassOperationDefinition` implementations.

```java
// Generated — do not edit
package cbs.dsl.codegen.generated.definitions;

public class LoanDisbursementEventDefinition implements EventDefinition {
    @Override public String getCode() { return "LOAN_DISBURSEMENT"; }
    @Override public List<ParameterDefinition> getParameters() { ... }
    @Override public Consumer<EnrichmentContext> getContextBlock() { ... }
    @Override public Consumer<TransactionsScope> getTransactionsBlock() { ... }
    @Override public BiConsumer<FinishContext, Throwable> getFinishBlock() { ... }
}
```

These generated definitions are also SPI-registered by a separate provider.

#### Layer 3 — Definition → Temporal (production) or Reflection (dev)

**Production (`GENERATED` mode):**

The `dsl-codegen` processor reads all compiled `*Definition` classes at compile time and generates Temporal-specific implementations:

```java
// Generated — do not edit
@WorkflowInterface
public interface LoanDisbursementEventWorkflow {
    @WorkflowMethod(name = "LOAN_DISBURSEMENT")
    void execute(EventInput input);
}

// Generated — do not edit
public class LoanDisbursementEventWorkflowImpl implements LoanDisbursementEventWorkflow {
    private final EventWorkflowOrchestrator orchestrator;
    // ... constructor injection ...

    @Override public void execute(EventInput input) {
        orchestrator.run(input, dslRegistry.resolveEvent("LOAN_DISBURSEMENT"));
    }
}
```

**Development (`REFLECTED` mode):**

No Temporal-specific classes are generated. A generic `ReflectiveWorkflow` and `ReflectiveActivity` wrapper interprets the `*Definition` metadata at runtime:

```java
public class ReflectiveWorkflow implements EventWorkflow {
    @Override public void execute(EventInput input) {
        EventDefinition def = dslRegistry.resolveEvent(input.eventCode());
        // Execute contextBlock, transactionsBlock, finishBlock via reflection
    }
}
```

### SPI-Based Loading at Startup

`SpiImplRegistryLoader` (in `dsl` module) loads all providers:

```java
public final class SpiImplRegistryLoader {
    public static void loadInto(ImplRegistry registry) {
        ServiceLoader.load(ImplRegistrationProvider.class).forEach(provider ->
            provider.register(registry)
        );
    }
}
```

`ImplRegistryAutoConfiguration` (in `starter` module) creates the Spring bean:

```java
@AutoConfiguration
public class ImplRegistryAutoConfiguration {
    @Bean
    public ImplRegistry implRegistry() {
        var registry = new ImplRegistry();
        SpiImplRegistryLoader.loadInto(registry);
        return registry;
    }
}
```

**Duplicate detection:** If two providers register DSL components with the same `code`, an
`IllegalStateException` is thrown with a clear message identifying the conflicting providers.

### Runtime Fallback for .java DSL Files

`ImplRegistry.populateFrom(DslRegistry)` provides a runtime fallback for definitions compiled from
`.java` DSL files at runtime. It scans and registers them by code and name. Test setups may still
pre-register `TestXxx` instances manually.

### Test Impl Registration

Test classes annotated with `@DslComponent` (e.g., `TestTransaction`, `TestHelper`, `TestCondition`)
participate in compile-time registration when the `dsl-codegen` processor is applied to the test
source set. The processor generates `GeneratedImplRegistrations` for both main and test classpaths.

### Lookup Priority in `ImplRegistry`

| Priority | Lookup Key           | Source                              | Use Case                                    |
|----------|----------------------|-------------------------------------|---------------------------------------------|
| 1        | Name-keyed entry     | `definition.getName()`              | Test overrides, named DSL stubs             |
| 2        | Code-keyed entry     | `definition.getCode()`              | Production beans, compiled DSL definitions  |
| 3        | Class-name lookup    | `ImplRegistry.resolveByClassName()` | GENERATED mode code imports                 |

This means a `TestTransactionDefinition` registered under `name = "TestKycCheck"` takes precedence over the
production `KycCheckTransactionDefinition` registered under `code = "KYC_CHECK"` when the DSL calls
`ctx.helper("TestKycCheck", ...)`. The production bean is still reachable via `"KYC_CHECK"`.

### Module Dependencies

```
dsl-api  ←  interfaces: *Function, *Definition, context types
   ↑
dsl-codegen  ←  Layer 1: @DslComponent processor (Function → Definition + SPI)
              Layer 2: DslCompiler (DSL file → Definition)
              Layer 3-prod: Temporal workflow/activity generator
   ↑
dsl          ←  Layer 3-dev: ReflectiveWorkflow, ReflectiveActivity
              SpiImplRegistryLoader
   ↑
starter      ←  ImplRegistryAutoConfiguration
   ↑
backend      ←  Temporal worker registration, HTTP controllers
```

The `WritableRegistry` interface in `dsl-api` breaks circular dependencies: `dsl-codegen` depends only on `dsl-api`, not on `dsl` or `starter`.

### Annotation Usage Examples

**Transaction implementation:**
```java
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
public class KycCheckTransaction implements TransactionFunction<KycCheckInput, KycCheckOutput> {
    @Override public KycCheckOutput preview(KycCheckInput input) { ... }
    @Override public KycCheckOutput execute(KycCheckInput input) { ... }
    @Override public KycCheckOutput rollback(KycCheckInput input) { ... }
}
```

**Helper implementation:**
```java
@DslComponent(code = "LOAN_CONDITIONS_BY_ID", type = DslImplType.HELPER)
public class LoanConditionsHelper implements HelperFunction<LoanConditionsInput, LoanConditionsOutput> {
    @Override public LoanConditionsOutput execute(LoanConditionsInput input) { ... }
}
```

**Condition implementation:**
```java
@DslComponent(code = "BORROWER_ACCOUNT_READY", type = DslImplType.CONDITION)
public class BorrowerAccountReadyCondition implements ConditionFunction<BorrowerInput, BorrowerOutput> {
    @Override public BorrowerOutput evaluate(BorrowerInput input) { ... }
}
```

**Test implementation (override):**
```java
@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
public class TestKycCheck implements TransactionFunction<TestInput, TestOutput> {
    @Override public TestOutput execute(TestInput input) {
        return new TestOutput(Map.of("kycVerified", true, "_implClass", "TestTransaction"));
    }
    @Override public TestOutput preview(TestInput input) { return execute(input); }
    @Override public TestOutput rollback(TestInput input) { return new TestOutput(Map.of()); }
}
```

---

## 4.12 JSON-Native Parameters & Avaje Jsonb

### Philosophy

All parameters flowing into and out of DSL components are **JSON-native**. This means:

- Every `XxxInput` and `XxxOutput` type is a plain data structure that serializes cleanly to JSON.
- No non-JSON types (e.g. raw `Map<String, Object>`, `java.sql.ResultSet`, or opaque Spring beans) cross DSL boundaries.
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
- `io.avaje:avaje-jsonb-generator:3.11` (Java APT Annotation Processor)

### Input/Output Types

Every DSL definition interface declares a typed `execute(input)` (or `evaluate(input)`) method. The input and output types are annotated with `@Json` so Avaje can generate adapters:

```java
@Json
public record TransactionInput(
  String agreementId,
  BigDecimal amount,
  String currency,
  String accountNumber   // nullable = optional parameter
) implements DslInput {}

@Json
public record TransactionOutput(
  String transactionId,
  String status
) implements DslOutput {}
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

Java nullability (via `@Nullable` or optional record components) determines whether a parameter is required or optional:

- **Required:** non-nullable type (`String`, `BigDecimal`, `Long`)
- **Optional:** nullable type (`String accountNumber`) or field with a default value

At compile time, the `dsl-codegen` Java APT processor inspects the `Input` class referenced by each `@DslComponent` and derives `List<ParameterDefinition>` from the class properties. This metadata is registered alongside the component so the engine can validate parameter presence before execution.

```java
@Json
public record KycCheckInput(
  String customerId,        // required
  String documentType       // optional (nullable)
) implements TransactionInput {}

@DslComponent(code = "KYC_CHECK", type = DslImplType.TRANSACTION)
public class KycCheckTransaction implements TransactionDefinition {
    @Override public String getCode() { return "KYC_CHECK"; }

    @Override public TransactionOutput execute(KycCheckInput input) {
        // input.customerId() is guaranteed non-null
        // input.documentType() may be null
        return new TransactionOutput("tx-123", "SUCCESS");
    }

    @Override public void preview(KycCheckInput input) { ... }
    @Override public void rollback(KycCheckInput input) { ... }
}
```

### Module Dependencies

```
dsl-api  ←  avaje-jsonb (API only, no runtime reflection)
   ↑
dsl-codegen  ←  inspects @Json classes, generates ParameterDefinition metadata
   ↑
  dsl    ←  runtime uses Jsonb.builder() to adapt Map<String, Object> → typed Input
   ↑
starter  ←  provides Jsonb bean
```

### Migration Notes

- `MapHelperInput` and `AnyHelperOutput` are deprecated in favor of typed `@Json` classes.
- `HelperFunction<I, O>` is removed; `HelperDefinition` directly declares `HelperOutput execute(HelperInput input)`.
- Builders in `dsl/runtime/` continue to work but wrap raw `Map<String, Object>` into the typed `Input` via Avaje before invoking `execute`.

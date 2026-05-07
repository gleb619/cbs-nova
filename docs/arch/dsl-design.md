# DSL Design Reference (Java)

← [Back to TDD](../tdd.md)

> **Note:** Workflows and Mass Operations have been moved to [dsl-workflows-massops.md](dsl-workflows-massops.md).

---

## File & Folder Convention

Each event owns a folder. All DSL objects for that event live in the same folder. Mass operation DSL files live in their
own folders under `cbs-rules/mass-operations/`.

```
cbs-rules/
├── global/
│   └── BankingHelpers.helper.java          ← available to all events
│
├── loan-disbursement/
│   ├── LoanDisbursementEvent.java
│   ├── DebitFundingAccountTransaction.java
│   ├── CreditBorrowerAccountTransaction.java
│   └── LoanHelpers.helper.java             ← scoped to this event only
│
├── loan-onboarding/
│   ├── LoanOnboardingEvent.java
│   └── KycCheckTransaction.java
│
└── mass-operations/
    ├── interest-charge/
    │   └── InterestChargeMassOperation.java
    └── penalty-accrual/
        └── PenaltyAccrualMassOperation.java
```

---

## Java 25 Implicit Classes

CBS-Nova DSL files leverage **Java 25 implicit classes** (JEP 445/463). Each `.java` file contains only `import`
statements followed by top-level fluent builder calls — no explicit `class` declaration.

The imports bring in DSL builder types (`EventDsl`, `WorkflowDsl`, `TransactionDsl`, `HelperDsl`, `ConditionDsl`,
`MassOperationDsl`) and framework API types (`Action`, `Signal`, `ExecutionContext`, etc.).

### Import System

```java
// Import all objects from an event folder (package)
import loan.disbursement.*;

// Import a specific helper file
import global.BankingHelpers;

// Import framework types
import cbs.dsl.api.ExecutionContext;
import cbs.dsl.api.Action;
```

### Two-pass Compilation

```
Pass 1 — Parse all .java DSL files, extract annotations and fluent builder calls, register into merged DslRegistry.
Pass 2 — Resolve imports against registry, validate all referenced definitions exist.
```

---

## Execution Modes

| Mode        | Environment                          | Source                          |
|-------------|--------------------------------------|---------------------------------|
| `GENERATED` | production / CI                      | compiled generated Java classes |
| `REFLECTED` | development only (`@Profile("dev")`) | raw `.java` via reflection      |

**Generated Mode:** `dsl-codegen` processor reads `@DslComponent` classes → generates `*Definition` wrappers → Layer 3
generates Temporal `Workflow` and `Activity` implementations.

**Reflected Mode:** `DevDslController` uses `DevDslEvaluator` to execute `.java` DSL via reflection. Intended for local
iteration only.

---

## Event DSL

```java
event("LOAN_DISBURSEMENT")
    .parameters(reg ->
      reg.string("customerId")
         .number("loanId")
         .decimal("amount")
         .string("accountNumber")
    )
  
    // Pre-Temporal enrichment
    .context(ctx -> {
        ctx.put("customerCode", ctx.helper("FIND_CUSTOMER_CODE_BY_ID",
            Map.of("id", ctx.get("customerId"))));
        ctx.put("loanConditions", ctx.helper("LOAN_CONDITIONS_BY_ID",
            Map.of("loanId", ctx.get("loanId"))));
    })

    // Transaction orchestration
    .transactions(ctx -> {
        var compliance = ctx.step("KYC_CHECK")
            .then("BLACKLIST_CHECK");
        var scoring = ctx.step("CREDIT_SCORING");

        ctx.await(compliance, scoring);

        // Conditional step
        var debit = ctx.stepWhen(ctx.get("loanConditions") != null)
            .then("DEBIT_FUNDING_ACCOUNT")
            .otherwise("DEBIT_FALLBACK_ACCOUNT");

        ctx.await(debit);
    })

    // Runs on success and failure
    .finish((ctx, ex) -> {
        if (ex != null) {
            ctx.helper("SEND_FAULT_NOTIFICATION", Map.of(
                "customerId", ctx.get("customerId"),
                "error", ex.getMessage()
            ));
        } else {
            ctx.prolong(Action.APPROVE);
        }
    });
```

> **Temporal Integration Note:** Events are **DSL-only** — they cannot be written as `@DslComponent` code classes. An Event is the only top-level Temporal Workflow; all execution starts as an `Event`. An Event generates both a Temporal **Workflow** (`EventWorkflow`) and an **Activity** (`EventActivity`). It is the only top-level Temporal Workflow; all execution starts as an `Event`.

---

## Transaction DSL

Transactions are units of work with `preview()`, `execute()`, and `rollback()` phases.

### Code-based (recommended for reusable logic)

```java
@DslComponent(code = "DEBIT_FUNDING_ACCOUNT", type = DslImplType.TRANSACTION)
public class DebitFundingAccountTransaction
    implements TransactionFunction<DebitInput, DebitOutput> {

    @Override public TransactionContext<DebitOutput> preview(TransactionContext<DebitInput> ctx) { ... }

    @Override public TransactionContext<DebitOutput> execute(TransactionContext<DebitInput> ctx) {
        var result = AppContext.resolve(DebitFundingAccountService.class)
            .debit(input.accountCode(), input.amount(), input.currency());
        //TODO: fix next line
        return new DebitOutput(result);
    }

    @Override public TransactionContext<DebitOutput> rollback(TransactionContext<DebitInput> ctx) {
        AppContext.resolve(DebitFundingAccountService.class)
            .postCompensatingEntry(input.txId(), true);
        //TODO: fix next line
        return new DebitOutput(Map.of("compensated", true));
    }
}
```

### DSL inline transactions

```java
transaction("DEBIT_FUNDING_ACCOUNT")
    .preview(ctx -> ExecutionResult.success("DEBIT_FUNDING_ACCOUNT", Map.of(
        "description", "Will debit " + ctx.get("accountCode")
    )))

    .execute(ctx -> {
        var result = ctx.resolve(DebitFundingAccountService.class)
            .debit((String) ctx.get("accountCode"), ctx.get("amount"), "USD");
        ctx.put("debitTxId", result.get("transactionId"));
        return ExecutionResult.success("DEBIT_FUNDING_ACCOUNT", result);
    })

    .rollback(ctx -> {
        ctx.delegate();
        ctx.resolve(DebitFundingAccountService.class)
            .postCompensatingEntry((String) ctx.get("debitTxId"), true);
    });
```

### DSL Reading Values Reference

| Source                     | DSL syntax                                           |
|----------------------------|------------------------------------------------------|
| Required parameter         | `ctx.get("customerId")`                              |
| Optional parameter         | `ctx.getOrDefault("accountNumber", "default")`       |
| Pre-evaluated context      | `ctx.get("customerCode")`                            |
| Earlier transaction output | `ctx.transactionResult("KYC_CHECK").get("verified")` |
| Helper call                | `ctx.helper("NAME", Map.of("key", value))`           |
| Spring bean                | `ctx.resolve(MyBean.class).myMethod()`               |
| Call interface method      | `ctx.delegate()`                                     |

---

## Helper DSL

Helpers are reusable computations implementing `HelperFunction<I, O>`. They are invoked via `ctx.helper("NAME", Map.of(...))` and can be chained.

> **Temporal Integration Note:** Helpers do **NOT** generate Temporal artifacts. They are plain Java/DSL logic executed synchronously inside Transaction or Condition activities. Only `Event`, `Transaction`, and `Condition` generate Temporal classes.

### Code-based (recommended)

```java
@DslComponent(code = "LOAN_CONDITIONS_BY_ID", type = DslImplType.HELPER)
public class LoanConditionsHelper
    implements HelperFunction<LoanConditionsInput, LoanConditionsOutput> {

    @Override public HelperContext<LoanConditionsOutput> execute(HelperContext<LoanConditionsInput> ctx) {
        //TODO: fix next line
        return new LoanConditionsOutput(Map.of("loanId", input.loanId(), "currency", "USD"));
    }
}
```

### Inline DSL helpers

```java
// loan-disbursement/LoanHelpers.helper.java
helpers()
    .helper("LOAN_CONDITIONS_BY_ID", ctx ->
        ctx.helper("CURRENCY_BY_AGREEMENT", Map.of(
            "agreementId", ctx.params().get("agreementId")
        ))
    )

    .helper("KYC_STATUS_BY_CUSTOMER", ctx ->
        ctx.helper("HTTP_GET", Map.of(
            "url", "https://kyc-service/api/status/" + ctx.params().get("customerId")
        ))
    );
```

```java
// global/BankingHelpers.helper.java (available to all events)
helpers()
    .helper("FIND_BANK_ACCOUNT", ctx ->
        ctx.resolve(AccountRepository.class).findByIban((String) ctx.params().get("iban"))
    )

    .helper("FIND_CUSTOMER_CODE_BY_ID", ctx ->
        ctx.resolve(CustomerRepository.class).findCodeById((String) ctx.params().get("id"))
    );
```

---

## Condition DSL

```java
// Code-based
@DslComponent(code = "BORROWER_ACCOUNT_READY", type = DslImplType.CONDITION)
public class BorrowerAccountReadyCondition
    implements ConditionFunction<BorrowerAccountInput, BorrowerAccountOutput> {

    @Override public ConditionContext<BorrowerAccountOutput> evaluate(ConditionContext<BorrowerAccountInput> ctx) {
        var account = (Map<?,?>) ctx.helper("FIND_BANK_ACCOUNT",
            Map.of("iban", input.accountCode()));
        //TODO: fix next line
        return new BorrowerAccountOutput(account != null && "ACTIVE".equals(account.get("status")));
    }
}
```

```java
// DSL inline
condition("BORROWER_ACCOUNT_READY", ctx -> {
    var account = (Map<?,?>) ctx.helper("FIND_BANK_ACCOUNT",
        Map.of("iban", ctx.get("accountCode")));
    return account != null && "ACTIVE".equals(account.get("status"));
});
```

### Using Conditions in Events

```java
.transactions(ctx -> {
    var credit = ctx.stepWhen(ctx.condition("BORROWER_ACCOUNT_READY"))
        .then("CREDIT_BORROWER_ACCOUNT");

    // Multi-branch
    var routing = ctx.stepWhen((Long) ctx.get("amount") > 10_000_000L)
        .then("HIGH_VALUE_DEBIT")
        .orWhen("USD".equals(ctx.get("currency")))
        .then("FOREIGN_CURRENCY_DEBIT")
        .otherwise("STANDARD_DEBIT");

    ctx.await(credit, routing);
})
```

---

## Function Interface Reference

//TODO: Fix next table, due new changes, we added *Context to executable methods

| `*Function` Interface         | Source         | Generated `*Definition`   | Temporal Artifact | Method(s)                                 |
|-------------------------------|----------------|---------------------------|-------------------|-------------------------------------------|
| `TransactionFunction<I, O>`   | Code or DSL    | `TransactionDefinition`   | `TransactionActivity` (Activity only) | `preview(I)`, `execute(I)`, `rollback(I)` |
| `HelperFunction<I, O>`        | Code or DSL    | `HelperDefinition`        | **None** (plain logic, no Temporal artifact) | `preview(I)`, `execute(I)`                |
| `ConditionFunction<I, O>`     | Code or DSL    | `ConditionDefinition`     | `ConditionActivity` (Activity only) | `evaluate(I)`                             |
| `EventFunction<I, O>`         | DSL only       | `EventDefinition`         | `EventWorkflow` + `EventActivity` (Event only) | `preview(I)`, `execute(I)`                |
| `WorkflowFunction<I, O>`      | DSL only       | `WorkflowDefinition`      | **None** (state machine DSL only) | `preview(I)`, `execute(I)`                |
| `MassOperationFunction<I, O>` | DSL only       | `MassOperationDefinition` | **None** (out of scope) | `preview(I)`, `execute(I)`                |

`@DslComponent` is only valid on `TransactionFunction`, `HelperFunction`, and `ConditionFunction`. `EventFunction`, `WorkflowFunction`, and `MassOperationFunction` are DSL-only and do not support `@DslComponent`.

---

## JSON-Native Parameters (Avaje Jsonb)

All parameters are **JSON-native** — every `XxxInput` and `XxxOutput` is a plain data structure that serializes cleanly
to JSON.

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

**Required vs Optional:** Non-nullable types are required; nullable types are optional.

### Module Dependencies

```
dsl-api      ←  avaje-jsonb (API only, no runtime reflection)
   ↑
dsl-codegen  ←  inspects @Json classes, generates ParameterDefinition metadata
   ↑
  dsl        ←  runtime uses Jsonb.builder() to adapt Map<String, Object> → typed Input
   ↑
starter      ←  provides Jsonb bean
```
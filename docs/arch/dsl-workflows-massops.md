# Workflows & Mass Operations DSL (Java)

← [Back to DSL Design](dsl-design.md) | [Events, Transactions & Helpers →](dsl-events-transactions-helpers.md)

---

## Workflow DSL — State Transitions

Transitions are full closures receiving `ctx`. Each transition can run multiple events — async by default, or explicitly awaited. `ctx.resumeEvent()` loads existing context from PostgreSQL and re-runs `finish {}` and `display {}` only — it sets `ctx.isResumed = true` so DSL authors can branch on it.

`onFault {}` is a closure per transition. `states`, `initial`, and `terminal` are optional; defaults apply if omitted.

```java
workflow("LOAN_CONTRACT")
    .states("DRAFT", "ENTERED", "ACTIVE", "CANCELLED", "CLOSED", "FAULTED")
    .initial("ENTERED")
    .terminal("CLOSED", "CANCELLED")

    .transition("DRAFT", "ENTERED", Action.SUBMIT, ctx -> {
        ctx.runEvent("LOAN_CREATE_AGREEMENT");
        ctx.runEvent("LOAN_ONBOARDING_NOTIFICATION");
    })

    .transition("ENTERED", "ACTIVE", Action.APPROVE, ctx -> {
        ctx.resumeEvent("LOAN_DISBURSEMENT"); // loads saved context
    })
    .onFault(ctx -> ctx.setStatus("FAULTED"))

    .transition("ENTERED", "CANCELLED", Action.CANCEL, ctx ->
        ctx.runEvent("LOAN_CANCELLATION"))

    .transition("ACTIVE", "CLOSED", Action.CLOSE, ctx ->
        ctx.runEvent("LOAN_CLOSURE"))

    .transition("FAULTED", "ENTERED", Action.ROLLBACK, ctx ->
        ctx.runEvent("LOAN_FAULT_COMPENSATION"));
```

**`runEvent` vs `resumeEvent`:**

| Method                   | context {}     | transactions {} | finish {} | display {} | ctx.isResumed |
|--------------------------|----------------|-----------------|-----------|------------|---------------|
| `ctx.runEvent(event)`    | recalculated   | executed        | executed  | executed   | false         |
| `ctx.resumeEvent(event)` | loaded from PG | skipped         | executed  | executed   | true          |

---

## MassOperation DSL

```java
massOperation("INTEREST_CHARGE")
    .category("CREDITS")

    // Triggers (one or more required)
    .cron("0 1 * * *")
    .once("2025-12-31T23:59:00")
    .onSignal(Signal.external("INTEREST_CHARGE_TRIGGER"))
    .onSignal(Signal.from("PENALTY_ACCRUAL", Signal.COMPLETED))

    // Shared context (evaluated once before processing)
    .context(ctx -> {
        ctx.put("businessDate", ctx.getOrDefault("date",
            ctx.helper("CURRENT_BUSINESS_DATE", Map.of())));
        ctx.put("interestRates", ctx.helper("LOAD_INTEREST_RATE_TABLE",
            Map.of("date", ctx.get("businessDate"))));
    })

    // Data source — collection of items to process
    .source(ctx -> ctx.helper("SQL_CLIENT", Map.of(
        "QUERY", "SELECT agreement_id, customer_id, outstanding_balance, currency
                  FROM credit_agreements
                  WHERE status = 'ACTIVE' AND next_interest_date <= :businessDate",
        "PARAMS", Map.of("businessDate", ctx.get("businessDate"))
    )))

    // Business lock
    .lock(ctx -> {
        var running = (Long) ctx.helper("SQL_CLIENT", Map.of(
            "QUERY", "SELECT COUNT(*) FROM mass_operation_execution
                      WHERE code = 'INTEREST_CHARGE' AND status = 'RUNNING'
                      AND started_at > NOW() - INTERVAL '24 hours'",
            "PARAMS", Map.of()
        ));
        return running == 0L;
    })

    // Per-item execution
    .item(ctx -> {
        var item = (Map<?,?>) ctx.get("item");
        var agreementId = (String) item.get("agreement_id");

        if (Boolean.TRUE.equals(item.get("has_workflow"))) {
            ctx.runWorkflow("LOAN_CONTRACT", Action.APPROVE,
                (Long) item.get("event_number"), Map.of(
                    "agreementId", agreementId,
                    "businessDate", ctx.get("businessDate"),
                    "rate", ((Map<?,?>) ctx.get("interestRates")).get(item.get("currency"))
                ));
        } else {
            ctx.runEvent("INTEREST_CHARGE_EVENT", Map.of(
                "agreementId", agreementId,
                "businessDate", ctx.get("businessDate"),
                "rate", ((Map<?,?>) ctx.get("interestRates")).get(item.get("currency"))
            ));
        }
    })

    // Signals
    .partial(1000, ctx -> {
        ctx.put("processedSoFar", ctx.processedCount());
    })

    .completed(ctx -> {
        ctx.put("totalProcessed", ctx.processedCount());
        ctx.put("totalFailed", ctx.failedCount());
    })

    // Post-execution hook
    .finish((ctx, ex) -> {
        if (ctx.failedCount() > 0) {
            ctx.helper("SEND_BATCH_FAILURE_REPORT", Map.of(
                "operation", "INTEREST_CHARGE",
                "failed", ctx.failedCount(),
                "total", ctx.processedCount()
            ));
        }
    });
```

### Mass Operation Context Variables

| Variable                       | Description                                |
|--------------------------------|--------------------------------------------|
| `ctx.get("item")`              | Current source row (Map from data source)  |
| `ctx.isMassOperation()`        | `true` when called from a mass operation   |
| `ctx.get("massOperationCode")` | Code of the parent mass operation          |
| `ctx.get("businessDate")`      | From mass operation context block          |
| `ctx.isResumed()`              | `true` when called via `ctx.resumeEvent()` |
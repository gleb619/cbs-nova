import cbs.dsl.builder.Dsl;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import java.util.Map;

Dsl.transaction("DEBIT_FUNDING_ACCOUNT")
    // Input parameters from the event - raw values provided by the caller
    .parameters(reg -> reg.string("accountCode").decimal("amount").string("currency"))
    // Preview uses both parameters and enriched context values from the parent event
    .preview(ctx -> TransactionOutput.success(Map.of(
        "description",
            "Will debit " + ctx.eventParameters().get("accountCode")
                + " for " + ctx.eventParameters().get("amount")
                + " " + ctx.eventParameters().get("currency"),
        "customerCode", ctx.enrichment().getOrDefault("customerCode", "N/A")
    )))
    // Execute the debit using both parameters and enriched context values
    .execute(ctx -> TransactionOutput.success(Map.of(
        "transactionId", "TX-" + System.currentTimeMillis(),
        "accountCode", ctx.eventParameters().get("accountCode"),
        "amount", ctx.eventParameters().get("amount"),
        "currency", ctx.eventParameters().get("currency"),
        "customerCode", ctx.enrichment().getOrDefault("customerCode", "N/A"),
        "status", "DEBITED"
    )))
    // Rollback compensates using parameters to know what to reverse
    .rollback(ctx -> TransactionOutput.success(Map.of(
        "compensated", true,
        "accountCode", ctx.eventParameters().get("accountCode"),
        "amount", ctx.eventParameters().get("amount")
    )))
    .build();

import cbs.dsl.builder.Dsl;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import java.util.Map;

Dsl.transaction("CREDIT_BORROWER_ACCOUNT")
    // Input parameters from the event - raw values provided by the caller
    .parameters(reg -> reg.string("accountCode").decimal("amount").string("currency"))
    // Preview shows what will happen using both parameters and context enrichment
    .preview(ctx -> TransactionOutput.success(Map.of(
        "description",
            "Will credit " + ctx.eventParameters().get("accountCode")
                + " with " + ctx.eventParameters().get("amount")
                + " " + ctx.eventParameters().get("currency"),
        "customerCode", ctx.enrichment().getOrDefault("customerCode", "N/A")
    )))
    // Execute the credit using both parameters and enriched context values
    .execute(ctx -> TransactionOutput.success(Map.of(
        "transactionId", "TX-" + System.currentTimeMillis(),
        "accountCode", ctx.eventParameters().get("accountCode"),
        "amount", ctx.eventParameters().get("amount"),
        "currency", ctx.eventParameters().get("currency"),
        "customerCode", ctx.enrichment().getOrDefault("customerCode", "N/A"),
        "status", "CREDITED"
    )))
    .build();

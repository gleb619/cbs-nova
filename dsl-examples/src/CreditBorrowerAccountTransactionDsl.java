import cbs.dsl.api.DslObject;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.builder.Dsl;

import java.util.List;
import java.util.Map;

List<DslObject> define() {
    return List.of(Dsl.transaction("CREDIT_BORROWER_ACCOUNT")
        // Input parameters from the event - raw values provided by the caller
        .parameters(reg -> reg.string("accountCode").decimal("amount").string("currency"))
        // Preview shows what will happen using both parameters and context enrichment
        .preview(ctx -> TransactionContext.builder().params(Map.of(
            "description",
                "Will credit " + ctx.params().get("accountCode")
                    + " with " + ctx.params().get("amount")
                    + " " + ctx.params().get("currency"),
            "customerCode", ctx.params().getOrDefault("customerCode", "N/A")
        )).build())
        // Execute the credit using both parameters and enriched context values
        .execute(ctx -> TransactionContext.builder().params(Map.of(
            "transactionId", "TX-" + System.currentTimeMillis(),
            "accountCode", ctx.params().get("accountCode"),
            "amount", ctx.params().get("amount"),
            "currency", ctx.params().get("currency"),
            "customerCode", ctx.params().getOrDefault("customerCode", "N/A"),
            "status", "CREDITED"
        )).build())
        .build());
}

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
                "Will credit " + ctx.get("accountCode")
                    + " with " + ctx.get("amount")
                    + " " + ctx.get("currency"),
            "customerCode", ctx.get("customerCode")
        )).build())
        // Execute the credit using both parameters and enriched context values
        .execute(ctx -> TransactionContext.builder().params(Map.of(
            "transactionId", "TX-" + System.currentTimeMillis(),
            "accountCode", ctx.get("accountCode"),
            "amount", ctx.get("amount"),
            "currency", ctx.get("currency"),
            "customerCode", ctx.get("customerCode"),
            "status", "CREDITED"
        )).build())
        .build());
}

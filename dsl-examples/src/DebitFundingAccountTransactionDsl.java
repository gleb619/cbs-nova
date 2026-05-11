import cbs.dsl.api.DslObject;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.builder.Dsl;

import java.util.List;
import java.util.Map;

List<DslObject> define() {
    return List.of(Dsl.transaction("DEBIT_FUNDING_ACCOUNT")
        // Input parameters from the event - raw values provided by the caller
        .parameters(reg -> reg.string("accountCode").decimal("amount").string("currency"))
        // Preview uses both parameters and enriched context values from the parent event
        .preview(ctx -> TransactionContext.builder().params(Map.of(
            "description",
                "Will debit " + ctx.get("accountCode")
                    + " for " + ctx.get("amount")
                    + " " + ctx.get("currency"),
            "customerCode", ctx.get("customerCode")
        )).build())
        // Execute the debit using both parameters and enriched context values
        .execute(ctx -> TransactionContext.builder().params(Map.of(
            "transactionId", "TX-" + System.currentTimeMillis(),
            "accountCode", ctx.get("accountCode"),
            "amount", ctx.get("amount"),
            "currency", ctx.get("currency"),
            "customerCode", ctx.get("customerCode"),
            "status", "DEBITED"
        )).build())
        // Rollback compensates using parameters to know what to reverse
        .rollback(ctx -> TransactionContext.builder().params(Map.of(
            "compensated", true,
            "accountCode", ctx.get("accountCode"),
            "amount", ctx.get("amount")
        )).build())
        .build());
}

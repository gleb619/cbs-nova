import cbs.dsl.api.DslObject;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.builder.Dsl;

import java.util.List;
import java.util.Map;

List<DslObject> define() {
    return List.of(Dsl.transaction("DEBIT_FUNDING_ACCOUNT")
        // Input parameters from the event - raw values provided by the caller
        .parameters(reg -> reg.string("accountCode").decimal("amount").string("currency"))
        // Preview uses both parameters and enriched context values from the parent event
        .preview(ctx -> TransactionOutput.success(Map.of(
            "description",
                "Will debit " + ctx.params().get("accountCode")
                    + " for " + ctx.params().get("amount")
                    + " " + ctx.params().get("currency"),
            "customerCode", ctx.params().getOrDefault("customerCode", "N/A")
        )))
        // Execute the debit using both parameters and enriched context values
        .execute(ctx -> TransactionOutput.success(Map.of(
            "transactionId", "TX-" + System.currentTimeMillis(),
            "accountCode", ctx.params().get("accountCode"),
            "amount", ctx.params().get("amount"),
            "currency", ctx.params().get("currency"),
            "customerCode", ctx.params().getOrDefault("customerCode", "N/A"),
            "status", "DEBITED"
        )))
        // Rollback compensates using parameters to know what to reverse
        .rollback(ctx -> TransactionOutput.success(Map.of(
            "compensated", true,
            "accountCode", ctx.params().get("accountCode"),
            "amount", ctx.params().get("amount")
        )))
        .build());
}

import cbs.dsl.api.DslObject;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.builder.Dsl;

import java.util.List;
import java.util.Map;

List<DslObject> define() {
    return List.of(Dsl.transaction("CREDIT_SCORING")
        // Input parameters - customerId and loan amount (raw values from the caller)
        .parameters(reg -> reg.string("customerId").decimal("amount"))
        // Execute uses both parameters and enriched context (credit history from event context)
        .execute(ctx -> {
            int baseScore = 700;
            // creditHistory is populated in enrichment by the parent event's context block
            String creditHistory = (String) ctx.params().getOrDefault("creditHistory", "GOOD");
            int adjustment = "EXCELLENT".equals(creditHistory) ? 100 : "POOR".equals(creditHistory) ? -200 : 0;
            int score = baseScore + adjustment;
            boolean approved = score >= 650;
            return TransactionOutput.success(Map.of(
                "customerId", ctx.params().get("customerId"),
                "score", score,
                "approved", approved,
                "amount", ctx.params().get("amount")
            ));
        })
        .build());
}

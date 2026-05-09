import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.builder.Dsl;

import java.util.Map;

Dsl.transaction("KYC_CHECK")
    // Input parameter: the customer to verify (raw value from the caller)
    .parameters(reg -> reg.string("customerId"))
    // Execute uses both the parameter and enriched context (customer profile from event context)
    .execute(ctx -> {
        String customerId = (String) ctx.params().get("customerId");
        // riskLevel is populated in enrichment by the parent event's context block
        String riskLevel = (String) ctx.params().getOrDefault("riskLevel", "MEDIUM");
        boolean verified = !"HIGH".equals(riskLevel);
        return TransactionOutput.success(Map.of(
            "customerId", customerId,
            "verified", verified,
            "riskLevel", riskLevel
        ));
    })
    .build();

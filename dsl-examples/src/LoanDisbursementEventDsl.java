import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;

import java.util.List;
import java.util.Map;

List<DslObject> define() {
    return List.of(Dsl.event("LOAN_DISBURSEMENT")
        // Input parameters for event execution - raw values provided by the caller
        .parameters(reg -> reg.string("customerId")
                               .number("loanId")
                               .decimal("amount")
                               .string("accountNumber"))
        // Context enrichment: calculated values when parameters alone are not enough.
        // customerId is in parameters, but we need customerCode from a helper call.
        // loanId is in parameters, but we need loan conditions from a helper call.
        .context(ctx -> {
            ctx.set("customerCode", ctx.helper("FIND_CUSTOMER_CODE",
                Map.of("id", ctx.get("customerId"))));
            ctx.set("loanConditions", ctx.helper("LOAN_CONDITIONS_BY_ID",
                Map.of("loanId", ctx.get("loanId"))));
        })
        .transaction("KYC_CHECK")
        .transaction("CREDIT_SCORING")
        .transaction("DEBIT_FUNDING_ACCOUNT")
        .transaction("CREDIT_BORROWER_ACCOUNT")
        .finish((ctx, ex) -> {
            if (ex != null) {
                // Use both parameters (customerId) and enriched context values (customerCode)
                ctx.helperResolver().apply("SEND_FAULT_NOTIFICATION", Map.of(
                    "customerId", ctx.params().get("customerId"),
                    "customerCode", ctx.params().getOrDefault("customerCode", "N/A"),
                    "error", ex.getMessage()
                ));
            }
        })
        .build());
}

import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.context.HelperContext;
import cbs.dsl.builder.Dsl;

import java.util.Map;

// Helpers are reusable functions that can be called from event context blocks.
// They receive input params and return a HelperOutput with computed values.

/** Resolves a customer code from an external identifier. */
Object obj = Dsl.helpers()
    .helper("FIND_CUSTOMER_CODE", h -> h
        .parameters(reg -> reg
            .string("id")
            .longNumber("loanId"))

        .context(ctx ->
            ctx.put("customerCode", ctx.helper("FIND_CUSTOMER_CODE_BY_ID", Map.of("id", ctx.get("customerId"))))
               .put("loanConditions", ctx.helper("LOAN_CONDITIONS_BY_ID", Map.of("loanId", ctx.get("loanId"))))
        )

        .execute(ctx -> ctx)
    )

    /** Retrieves loan conditions for a given loan identifier. */
    .helper("LOAN_CONDITIONS_BY_ID", h -> h
        .parameters(reg -> reg.number("loanId"))
        .execute(input -> HelperContext.builder()
            .params(input.params())
            .build()))
    /** Sends a fault notification to the customer via the configured channel. */
    .helper("SEND_FAULT_NOTIFICATION", h -> h
        .parameters(reg -> reg.string("customerId").string("error"))
        .execute(input -> HelperContext.builder()
            .params(input.params())
            .build()))
    .build();

void main() {
  Dsl.register(obj);
}
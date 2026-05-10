import cbs.dsl.api.DslObject;
import cbs.dsl.api.context.HelperContext;
import cbs.dsl.builder.Dsl;

import java.util.List;
import java.util.Map;

// Helpers are reusable functions that can be called from event context blocks.
// They receive input params and return a HelperOutput with computed values.

/** Resolves a customer code from an external identifier. */
List<DslObject> define() {
    return Dsl.helpers()
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
            .execute(ctx -> ctx.put("aaa", "bbb"))
        )
        /** Sends a fault notification to the customer via the configured channel. */
        .helper("SEND_FAULT_NOTIFICATION", h -> h
            .parameters(reg -> reg.string("customerId").string("error"))
            .context(ctx -> ctx.remove("error"))
            .execute(ctx -> HelperContext.builder()
                .params(ctx.params())
                .build())
        )
        .build();
}

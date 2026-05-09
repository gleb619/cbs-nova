import cbs.dsl.builder.Dsl;
import cbs.dsl.api.HelperTypes.HelperOutput;
import java.util.Map;

// Helpers are reusable functions that can be called from context blocks.
// They receive input params and return a HelperOutput with computed values.

Dsl.helpers()
    .helper("FIND_CUSTOMER_CODE", h -> h
        .parameters(reg -> reg.string("id"))
        .execute(input -> new HelperOutput(Map.of(
            "customerCode", "CUST-" + input.params().get("id")
        ))))
    .helper("LOAN_CONDITIONS_BY_ID", h -> h
        .parameters(reg -> reg.number("loanId"))
        .execute(input -> new HelperOutput(Map.of(
            "loanId", input.params().get("loanId"),
            "currency", "USD",
            "interestRate", "5.5"
        ))))
    .helper("SEND_FAULT_NOTIFICATION", h -> h
        .parameters(reg -> reg.string("customerId").string("error"))
        .execute(input -> new HelperOutput(Map.of(
            "sent", true,
            "channel", "EMAIL"
        ))))
    .build();

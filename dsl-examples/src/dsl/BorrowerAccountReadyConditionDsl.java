import cbs.dsl.builder.Dsl;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import java.util.Map;

Dsl.condition("BORROWER_ACCOUNT_READY")
    // Input parameter: the account code to check (provided by the caller/event)
    .parameters(reg -> reg.string("accountCode"))
    // Evaluate using both input parameters and enriched context values.
    // accountStatus is expected to be populated in enrichment by the parent event's context block.
    .evaluate(ctx -> new ConditionOutput(
        "ACTIVE".equals(ctx.enrichment().get("accountStatus"))
            && ctx.eventParameters().get("accountCode") != null
    ))
    .build();

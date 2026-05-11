import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;

import java.util.List;
import java.util.Map;

List<DslObject> define() {
    return List.of(Dsl.condition("BORROWER_ACCOUNT_READY")
        // Input parameter: the account code to check (provided by the caller/event)
        .parameters(reg -> reg.string("accountCode"))
        // Evaluate using both input parameters and enriched context values.
        // accountStatus is expected to be populated in enrichment by the parent event's context block.
        .check(ctx -> ctx.result(
            "ACTIVE".equals(ctx.get("accountStatus"))
                && ctx.get("accountCode") != null
        ))
        .build());
}

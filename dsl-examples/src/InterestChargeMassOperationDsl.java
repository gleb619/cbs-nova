import cbs.dsl.api.DslObject;
import cbs.dsl.api.MassOperationTypes.MassOperationInput;
import cbs.dsl.builder.Dsl;

import java.time.Instant;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
    return List.of(Dsl.massOperation("INTEREST_CHARGE")
        .category("BATCH")
        // Input parameters for the mass operation - raw values provided by the caller
        .parameters(reg -> reg.string("productCode").decimal("rate"))
        // Context enrichment: prepare batch-wide calculated values
        .context(ctx -> {
            ctx.enrichment().put("batchId", "BATCH-" + System.currentTimeMillis());
            ctx.enrichment().put("processedAt", Instant.now().toString());
        })
        // Process each item using enriched context (parameters + calculated values)
        .item(ctx -> {
//            var input = (MassOperationInput) ctx.payload();
//            String productCode = (String) input.params().get("productCode");
            // Item processing logic uses both the input productCode and enriched batchId
        })
        .build());
}

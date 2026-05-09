import cbs.dsl.builder.Dsl;
import java.util.Map;

Dsl.massOperation("INTEREST_CHARGE")
    .category("BATCH")
    // Input parameters for the mass operation - raw values provided by the caller
    .parameters(reg -> reg.string("productCode").decimal("rate"))
    // Context enrichment: prepare batch-wide calculated values
    .context(ctx -> {
        ctx.enrichment().put("batchId", "BATCH-" + System.currentTimeMillis());
        ctx.enrichment().put("processedAt", java.time.Instant.now().toString());
    })
    // Process each item using enriched context (parameters + calculated values)
    .item(ctx -> {
        String productCode = (String) ctx.enrichment().get("productCode");
        // Item processing logic uses both the input productCode and enriched batchId
    })
    .build();

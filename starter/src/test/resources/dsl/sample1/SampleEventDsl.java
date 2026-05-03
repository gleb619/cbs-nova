import cbs.dsl.builder.EventDsl;
import java.util.Map;

EventDsl.event("SAMPLE_EVENT_DSL")
    .requiredParam("name")
    .context(ctx -> {
      Object helperResult = ctx.helper("SAMPLE_HELPER", Map.of("someVal", ctx.get("name")));
      ctx.put("enriched", helperResult);
    })
    .transaction("SAMPLE_TX")
    .transaction("SAMPLE_TRANSACTION_DSL")
    .finish((ctx, ex) -> {})
    .build();

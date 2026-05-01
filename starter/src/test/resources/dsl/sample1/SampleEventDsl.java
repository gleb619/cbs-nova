import cbs.dsl.builder.EventDsl;
import cbs.dsl.api.EventDefinition;
import java.util.Map;

static EventDefinition SAMPLE_EVENT_DSL = EventDsl.event("SAMPLE_EVENT_DSL")
    .requiredParam("name")
    .context(ctx -> {
      Object helperResult = ctx.helper("SAMPLE_HELPER", Map.of("someVal", ctx.get("name")));
      ctx.put("enriched", helperResult);
    })
    .transaction("SAMPLE_TX")
    .transaction("SAMPLE_TRANSACTION_DSL")
    .finish((ctx, ex) -> {})
    .build();

// JEP 512 compact source files require a main method even when the file is only
// loaded reflectively for its static fields.
void main() {
  //TODO:
}

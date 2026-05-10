import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;
import java.util.Map;

List<DslObject> define() {
  return Dsl.event("SAMPLE_EVENT_DSL")
      .parameters(reg -> reg.string("name"))
      .context(ctx -> {
        Object helperResult = ctx.helper("SAMPLE_HELPER", Map.of("someVal", ctx.get("name")));
        ctx.put("enriched", helperResult);
      })
      .transaction("SAMPLE_TX")
      .transaction("SAMPLE_TRANSACTION_DSL")
      .finish((ctx, ex) -> {
      })
      .build();
}
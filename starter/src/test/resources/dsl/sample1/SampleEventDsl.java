import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  /* We register a new event called `SAMPLE_EVENT_VIA_DSL` */
  return List.of(Dsl.event("SAMPLE_EVENT_VIA_DSL")
      /* We describe parameres, that needed to run event */
      .parameters(reg -> reg.string("name"))
      /* We call dsl declared helper to demonstrate the flexibility of customization. */
      .context(ctx -> {
        Object helperResult = ctx.runHelper("SAMPLE_HELPER", Map.of("someVal", ctx.get("name")));
        ctx.put("enriched", helperResult);
        return ctx;
      })
      /* We define transaction steps: one registered in code (`SAMPLE_TX`), one declared via DSL (`SAMPLE_TRANSACTION_VIA_DSL`), and we wait for both to finish. */
      .transactions(ctx -> {
        var txFromCode = ctx.step("SAMPLE_TX");
        var txFromDsl = ctx.step("SAMPLE_TRANSACTION_VIA_DSL");
        ctx.await(txFromCode, txFromDsl);
      })
      /* On finish we just store a completion marker. */
      .finish((ctx, ex) -> {
        ctx.put("completed", true);
      })
      .build());
}

import cbs.dsl.api.DslObject;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.builder.Dsl;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
    /* We register a new transaction called `SAMPLE_TRANSACTION_VIA_DSL` */
    return List.of(Dsl.transaction("SAMPLE_TRANSACTION_VIA_DSL")
        /* We describe parameres, that needed to run transaction */
        .parameters(reg -> reg.string("name"))
        /* We call dsl declared helper to demonstrate the flexibility of customization. */
        .context(ctx -> ctx.put("someName", ctx.runHelper("SAMPLE_HELPER_VIA_DSL", Map.of("name",
            ctx.get("name")))))
        /* On execute we launch another transaction called `SAMPLE_TX`, and pass parameters */
        .execute(ctx -> {
            ctx.runTransaction("SAMPLE_TX", Map.of("name", "DSL TX says hello to " + ctx.get("someName")));
            return ctx;
        })
        .build());
}

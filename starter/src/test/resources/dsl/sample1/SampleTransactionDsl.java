import cbs.dsl.api.DslObject;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.builder.Dsl;
import java.util.Map;

List<DslObject> define() {
    return Dsl.transaction("SAMPLE_TRANSACTION_DSL")
        .parameters(reg -> reg.string("name"))
        .execute(ctx -> TransactionContext.builder().params(Map.of("greeting", "DSL TX says hello to " + ctx.get("name"))).build())
        .build();
}
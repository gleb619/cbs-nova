import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import java.util.Map;

List<DslObject> define() {
    return Dsl.transaction("SAMPLE_TRANSACTION_DSL")
        .parameters(reg -> reg.string("name"))
        .execute(ctx -> TransactionOutput.success(Map.of("greeting", "DSL TX says hello to " + ctx.get("name"))))
        .build();
}
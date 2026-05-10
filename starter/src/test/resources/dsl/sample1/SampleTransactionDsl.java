import cbs.dsl.api.DslObject;
import cbs.dsl.builder.Dsl;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import java.util.Map;

List<DslObject> define() {
    return Dsl.transaction("SAMPLE_TRANSACTION_DSL")
        .parameters(reg -> reg.string("name"))
        //TODO: redo `TransactionBuilder`, make `execute` accept some TransactionContext, add there method:
        // `runTransaction` that will find correspondent transation in registry and execute it, so
        // SAMPLE_TRANSACTION_DSL became a wrapper for real `SAMPLE_TRANSACTION`
        .execute(ctx -> TransactionOutput.success(Map.of("greeting", "DSL TX says hello to " + ctx.get("name"))))
        .build();
}
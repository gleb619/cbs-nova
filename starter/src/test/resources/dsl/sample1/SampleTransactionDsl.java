import cbs.dsl.builder.TransactionDsl;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import java.util.Map;

TransactionDsl.transaction("SAMPLE_TRANSACTION_DSL")
    .requiredParam("name")
    .execute(ctx -> new TransactionOutput(Map.of("greeting", "DSL TX says hello to " + ctx.get("name"))))
    .build();

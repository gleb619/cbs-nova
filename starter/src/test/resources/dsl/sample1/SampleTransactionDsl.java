import cbs.dsl.builder.TransactionDsl;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import java.util.Map;

static TransactionDefinition SAMPLE_TRANSACTION_DSL = TransactionDsl.transaction("SAMPLE_TRANSACTION_DSL")
    .requiredParam("name")
    .execute(ctx -> new TransactionOutput(Map.of("greeting", "DSL TX says hello to " + ctx.get("name"))))
    .build();

// JEP 512 compact source files require a main method even when the file is only
// loaded reflectively for its static fields.
void main() {
}

package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslComponent.DslImplType;
import cbs.dsl.api.TransactionFunction;
import cbs.dsl.api.context.TransactionContext;
import cbs.nova.sample.SampleTransaction.SampleTxInput;
import cbs.nova.sample.SampleTransaction.SampleTxOutput;
import io.avaje.jsonb.Json;

import java.util.Map;

/** Sample transaction for the PoC. Produces a greeting from the {@code name} parameter. */
@DslComponent(code = "SAMPLE_TX", type = DslImplType.TRANSACTION)
public class SampleTransaction implements TransactionFunction<SampleTxInput, SampleTxOutput> {

  @Override
  public TransactionContext<SampleTxOutput> preview(TransactionContext<SampleTxInput> input) {
    //    return input.toBuilder().payload(new SampleTxOutput("preview: " +
    // input.payload().name())).build();
    return null;
  }

  @Override
  public TransactionContext<SampleTxOutput> execute(TransactionContext<SampleTxInput> input) {
    //    return input.toBuilder().payload(new SampleTxOutput("Hello, " +
    // input.payload().name())).build();
    return null;
  }

  @Override
  public TransactionContext<SampleTxOutput> rollback(TransactionContext<SampleTxInput> input) {
    //    return input.toBuilder().payload(new SampleTxOutput("rollback: " +
    // input.payload().name())).build();
    return null;
  }

  @Json
  public record SampleTxInput(String name) implements TransactionArg {

    @Override
    // TODO: replace with avaje serialization
    public Map<String, Object> params() {
      return Map.of("name", name);
    }
  }

  @Json
  public record SampleTxOutput(String greeting) implements TransactionResult {

    @Override
    // TODO: replace with avaje serialization
    public Map<String, Object> params() {
      return Map.of("greeting", greeting);
    }
  }
}

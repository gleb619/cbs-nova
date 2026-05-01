package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;
import cbs.dsl.api.TransactionFunction;
import cbs.nova.sample.SampleTransaction.SampleTxInput;
import cbs.nova.sample.SampleTransaction.SampleTxOutput;
import io.avaje.jsonb.Json;
import java.util.Map;

/** Sample transaction for the PoC. Produces a greeting from the {@code name} parameter. */
@DslComponent(code = "SAMPLE_TX", type = DslImplType.TRANSACTION)
public class SampleTransaction implements TransactionFunction<SampleTxInput, SampleTxOutput> {

  @Override
  public SampleTxOutput preview(SampleTxInput input) {
    return new SampleTxOutput("preview: " + input.name());
  }

  @Override
  public SampleTxOutput execute(SampleTxInput input) {
    return new SampleTxOutput("Hello, " + input.name());
  }

  @Override
  public SampleTxOutput rollback(SampleTxInput input) {
    return new SampleTxOutput("rollback: " + input.name());
  }

  @Json
  public record SampleTxInput(String name) implements TransactionArg {

    @Override
    public Map<String, Object> toMap() {
      return Map.of("name", name);
    }
  }

  @Json
  public record SampleTxOutput(String greeting) implements TransactionResult {

    @Override
    public Map<String, Object> toMap() {
      return Map.of("greeting", greeting);
    }
  }

}

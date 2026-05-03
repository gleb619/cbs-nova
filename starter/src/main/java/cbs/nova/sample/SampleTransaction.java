package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;
import cbs.dsl.api.TransactionFunction;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;

import java.util.Map;

/**
 * Sample transaction for the PoC. Simply validates that input context contains a {@code name}
 * parameter and returns it in the output.
 */
@DslComponent(code = "SAMPLE_TX", type = DslImplType.TRANSACTION)
public class SampleTransaction implements TransactionFunction<TransactionInput, TransactionOutput> {

  @Override
  public TransactionOutput preview(TransactionInput input) {
    return new TransactionOutput(Map.of());
  }

  @Override
  public TransactionOutput execute(TransactionInput input) {
    String name = input.params().getOrDefault("name", "world").toString();
    return new TransactionOutput(Map.of("greeting", "Hello, " + name + "!"));
  }

  @Override
  public TransactionOutput rollback(TransactionInput input) {
    return new TransactionOutput(Map.of());
  }
}

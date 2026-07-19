package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionBuilder;
import cbs.nova.dsl.process.ProcessBuilder;
import cbs.nova.dsl.transaction.TransactionBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Dsl {

  public static ProcessBuilder<Object, Object> process(String name) {
    return new ProcessBuilder<>(name);
  }

  public static TransactionBuilder<Object, Object> transaction(String name) {
    return new TransactionBuilder<>(name);
  }

  public static FunctionBuilder<Object, Object> function(String name) {
    return new FunctionBuilder<>(name);
  }
}

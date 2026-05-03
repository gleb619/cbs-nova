package cbs.dsl.api.context;

import cbs.dsl.api.TransactionDefinition;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface TransactionsScope {

  CompletableFuture<StepHandle> step(TransactionDefinition tx);

  CompletableFuture<StepHandle> step(Consumer<ConditionalStepBuilder> block);

  void await(StepHandle... handles);

  Object get(String key);

  void set(String key, Object value);
}

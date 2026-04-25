package cbs.dsl.api.context;

import cbs.dsl.api.TransactionDefinition;

import java.util.concurrent.CompletableFuture;

public interface StepHandle {

  CompletableFuture<StepHandle> then(TransactionDefinition tx);

  void join();
}

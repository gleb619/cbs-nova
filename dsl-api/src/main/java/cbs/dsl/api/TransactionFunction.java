package cbs.dsl.api;

import cbs.dsl.api.TransactionFunction.TransactionArg;
import cbs.dsl.api.TransactionFunction.TransactionResult;

@FunctionalInterface
public interface TransactionFunction<I extends TransactionArg, O extends TransactionResult> {

  default O preview(I input) {
    throw new IllegalStateException("Not implemented!");
  }

  O execute(I input);

  default O rollback(I input) {
    throw new IllegalStateException("Not implemented!");
  }

  interface TransactionArg extends DslPayload {}

  interface TransactionResult extends DslPayload {}
}

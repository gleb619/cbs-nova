package cbs.dsl.api;

import cbs.dsl.api.TransactionFunction.TransactionArg;
import cbs.dsl.api.TransactionFunction.TransactionResult;
import cbs.dsl.api.context.TransactionContext;

@FunctionalInterface
public interface TransactionFunction<I extends TransactionArg, O extends TransactionResult> {

  default TransactionContext<O> preview(TransactionContext<I> input) {
    throw new IllegalStateException("Not implemented!");
  }

  TransactionContext<O> execute(TransactionContext<I> input);

  default TransactionContext<O> rollback(TransactionContext<I> input) {
    throw new IllegalStateException("Not implemented!");
  }

  interface TransactionArg extends DslPayload {}

  interface TransactionResult extends DslPayload {}
}

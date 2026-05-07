package cbs.dsl.api;

import cbs.dsl.api.TransactionFunction.TransactionArg;
import cbs.dsl.api.TransactionFunction.TransactionResult;
import cbs.dsl.api.context.TransactionContext;
import io.avaje.jsonb.Jsonb;
import java.util.Map;

@FunctionalInterface
public interface TransactionFunction<I extends TransactionArg, O extends TransactionResult> {

  default TransactionContext<O> preview(TransactionContext<I> input) {
    throw new IllegalStateException("Not implemented!");
  }

  TransactionContext<O> execute(TransactionContext<I> input);

  default TransactionContext<O> rollback(TransactionContext<I> input) {
    throw new IllegalStateException("Not implemented!");
  }

  interface TransactionArg extends DslPayload {

    @Override
    default Map<String, Object> params() {
      return JsonPayload.toMap(this);
    }

  }

  interface TransactionResult extends DslPayload {

    @Override
    default Map<String, Object> params() {
      return JsonPayload.toMap(this);
    }

  }
}

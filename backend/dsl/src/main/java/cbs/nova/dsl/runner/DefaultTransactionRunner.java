package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionRichContext;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.jspecify.annotations.NonNull;

public final class DefaultTransactionRunner implements TransactionRunner {

  @Override
  public @NonNull Result<?> run(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    try {
      var richCtx = new TransactionRichContext<>(ctx);
      if (ctx.mode() == ExecutionMode.EXPLAIN) {
        var result = transaction.executeLogic().apply(richCtx);
        if (result.isSuccess()) {
          ctx = ctx.withMetadata("explain.description", "Transaction: " + transaction.name());
        }
        return result;
      }
      if (ctx.mode() == ExecutionMode.PREVIEW) {
        return transaction.effectivePreview().apply(richCtx);
      }
      // RUN: TODO wire to Temporal
      return transaction.executeLogic().apply(richCtx);
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      return Result.failure(new DslExecutionException(ctx.runId(), message, ex));
    }
  }
}

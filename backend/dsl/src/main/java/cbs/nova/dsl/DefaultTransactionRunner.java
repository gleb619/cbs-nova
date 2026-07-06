package cbs.nova.dsl;

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
      // TODO: wire to Temporal in RUN mode
      return transaction.executeLogic().apply(richCtx);
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      return Result.failure(new DslExecutionException(ctx.runId(), message, ex));
    }
  }
}

package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class DefaultTransactionRunner implements TransactionRunner {

  @Override
  public @NonNull Result<?> run(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    try {
      if (ctx.mode() == ExecutionMode.EXPLAIN) {
        var result = transaction.executeLogic().apply(ctx);
        if (result.isSuccess()) {
          ctx = ctx.withMetadata("explain.description", "Transaction: " + transaction.name());
        }
        return result;
      }
      // TODO: wire to Temporal in RUN mode
      return transaction.executeLogic().apply(ctx);
    } catch (Exception ex) {
      return Result.failure(ex);
    }
  }
}

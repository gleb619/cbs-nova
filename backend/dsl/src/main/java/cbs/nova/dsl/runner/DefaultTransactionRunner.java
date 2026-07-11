package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionRichContext;
import cbs.nova.dsl.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DefaultTransactionRunner implements TransactionRunner {

  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;

  public DefaultTransactionRunner() {
    this(new ExecutionTraceCollector(), new ContextFactory());
  }

  @Override
  public @NonNull Result<?> run(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    try {
      var richCtx = new TransactionRichContext<>(ctx, traceCollector, contextFactory);
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
      return transaction.executeLogic().apply(richCtx);
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      return Result.failure(new DslExecutionException(ctx.runId(), message, ex));
    }
  }
}

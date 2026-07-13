package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionExecution;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionRichContext;
import cbs.nova.dsl.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Instant;

@RequiredArgsConstructor
public final class DefaultTransactionRunner implements TransactionRunner {

  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;

  @Override
  public @NonNull Result<?> run(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    Result<?> result;
    try {
      var richCtx = new TransactionRichContext<>(ctx, traceCollector, contextFactory);
      if (ctx.mode() == ExecutionMode.EXPLAIN) {
        result = transaction.executeLogic().apply(richCtx);
        if (result.isSuccess()) {
          ctx = ctx.withMetadata("explain.description", "Transaction: " + transaction.name());
        }
      } else if (ctx.mode() == ExecutionMode.PREVIEW) {
        result = transaction.effectivePreview().apply(richCtx);
      } else {
        result = transaction.executeLogic().apply(richCtx);
      }
      notifyListener(ctx, transaction, result);
      return result;
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      var failure = Result.failure(new DslExecutionException(ctx.runId(), message, ex));
      notifyFailure(ctx, transaction, ex);
      return failure;
    }
  }

  private void notifyListener(
          @NonNull Context<?> ctx,
          @NonNull TransactionDslObject transaction,
          @NonNull Result<?> result) {
    var listener = ctx.executionListener();
    if (listener == null) {
      return;
    }
    if (result.isSuccess()) {
      listener.onTransactionSuccess(new TransactionExecution(
              ctx.runId(),
              transaction.name(),
              ctx.body(),
              Instant.now()));
    } else if (result.cause() != null) {
      listener.onTransactionFailure(ctx.runId(), transaction.name(), result.cause());
    }
  }

  private void notifyFailure(
          @NonNull Context<?> ctx,
          @NonNull TransactionDslObject transaction,
          @NonNull Throwable cause) {
    var listener = ctx.executionListener();
    if (listener != null) {
      listener.onTransactionFailure(ctx.runId(), transaction.name(), cause);
    }
  }
}

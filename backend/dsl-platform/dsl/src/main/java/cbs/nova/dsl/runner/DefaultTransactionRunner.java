package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.dsl.transaction.TransactionRichContext;
import cbs.nova.dsl.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
public final class DefaultTransactionRunner implements TransactionRunner {

  private final ContextFactory contextFactory;
  private final CompensationRegistry compensationRegistry;

  @Override
  public @NonNull Result<?> run(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    var listener = ctx.executionListener();
    if (listener != null) {
      listener.onTransactionStart(ctx.runId(), transaction.name(), ctx.body());
    }
    Result<?> result = null;
    try {
      var richCtx = new TransactionRichContext<>(ctx, contextFactory);
      if (ctx.mode() == ExecutionMode.EXPLAIN) {
        result = transaction.executeLogic().apply(richCtx);
      } else if (ctx.mode() == ExecutionMode.PREVIEW) {
        result = transaction.effectivePreview().apply(richCtx);
      } else {
        result = transaction.executeLogic().apply(richCtx);
      }
      notifyListener(ctx, transaction, result);
      if (result.isSuccess()) {
        registerCompensation(transaction, ctx);
      }
      return result;
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      var failure = Result.failure(new DslExecutionException(ctx.runId(), message, ex));
      notifyFailure(ctx, transaction, ex);
      return failure;
    } finally {
      if (listener != null) {
        listener.onTransactionEnd(ctx.runId(), transaction.name(),
                result != null ? result.value() : null, result != null && result.isSuccess());
      }
    }
  }

  private void registerCompensation(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    if (transaction.compensationLogic() == null) {
      return;
    }
    DslSaga saga = ctx.saga();
    if (saga != null) {
      saga.addCompensation(() -> {
        Object compensationBody = ctx.body();
        var compCtxBase = contextFactory.of(compensationBody, Map.of(),
                ExecutionMode.COMPENSATION, ctx.runId(), ctx.transactionRouting(),
                ctx.executionListener(), ctx.saga())
                .withExecutionTraceCollector(ctx.executionTraceCollector());
        var compCtx = GlobalManager.globalManager().createCompensationContext(compCtxBase,
                new RuntimeException("compensation triggered"));
        transaction.compensationLogic().apply(compCtx);
      });
      return;
    }
    compensationRegistry.register(transaction.name(), ctx.runId(), ctx, transaction);
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

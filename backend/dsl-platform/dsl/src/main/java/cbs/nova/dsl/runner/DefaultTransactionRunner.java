package cbs.nova.dsl.runner;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.dsl.transaction.TransactionExecutionStatus;
import cbs.nova.dsl.transaction.TransactionRichContext;
import cbs.nova.dsl.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
public final class DefaultTransactionRunner implements TransactionRunner {

  private final CompensationRegistry compensationRegistry;

  @Override
  public @NonNull Result<?> run(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    var listener = ctx.executionListener();
    var startedAt = Instant.now();
    listener.onTransactionStart(ctx.runId(), transaction.name(), ctx.body());
    Result<?> result = null;
    try {
      var richCtx = new TransactionRichContext<>(ctx);
      if (ctx.mode() == ExecutionMode.EXPLAIN) {
        result = transaction.effectiveExplain().apply(richCtx);
      } else if (ctx.mode() == ExecutionMode.PREVIEW || ctx.mode() == ExecutionMode.HIERARCHY) {
        result = transaction.effectivePreview().apply(richCtx);
      } else {
        result = transaction.executeLogic().apply(richCtx);
      }
      notifyListener(ctx, transaction, result, startedAt, Instant.now());
      if (result.isSuccess()) {
        registerCompensation(transaction, ctx);
      }
      return result;
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      var failure = Result.failure(new DslExecutionException(ctx.runId(), message, ex));
      notifyFailure(ctx, transaction, ex, startedAt, Instant.now());
      return failure;
    } finally {
      listener.onTransactionEnd(ctx.runId(), transaction.name(),
              result != null ? result.value() : null, result != null && result.isSuccess());
    }
  }

  private void registerCompensation(
          @NonNull TransactionDslObject transaction, @NonNull Context<?> ctx) {
    if (transaction.compensationLogic() == null) {
      return;
    }
    DslSaga saga = ctx.saga();
    if (!saga.isNoop()) {
      saga.addCompensation(() -> {
        Object compensationBody = ctx.body();
        var compCtxBase = SimpleContext.builder().body(compensationBody).metadata(Map.of())
                .mode(ExecutionMode.COMPENSATION).runId(ctx.runId())
                .transactionRouting(ctx.transactionRouting())
                .executionListener(ctx.executionListener()).saga(ctx.saga()).build()
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
          @NonNull Result<?> result,
          @NonNull Instant startedAt,
          @NonNull Instant finishedAt) {
    var listener = ctx.executionListener();
    if (result.isSuccess()) {
      listener.onTransactionSuccess(new TransactionExecution(
              ctx.runId(),
              transaction.name(),
              ctx.body(),
              finishedAt,
              startedAt,
              finishedAt,
              statusFor(true, ctx),
              null));
    } else if (result.cause() != null) {
      listener.onTransactionFailure(ctx.runId(), transaction.name(), result.cause());
      listener.onTransactionSuccess(new TransactionExecution(
              ctx.runId(),
              transaction.name(),
              ctx.body(),
              finishedAt,
              startedAt,
              finishedAt,
              statusFor(false, ctx),
              result.cause().getMessage()));
    }
  }

  private void notifyFailure(
          @NonNull Context<?> ctx,
          @NonNull TransactionDslObject transaction,
          @NonNull Throwable cause,
          @NonNull Instant startedAt,
          @NonNull Instant finishedAt) {
    var listener = ctx.executionListener();
    listener.onTransactionFailure(ctx.runId(), transaction.name(), cause);
    listener.onTransactionSuccess(new TransactionExecution(
            ctx.runId(),
            transaction.name(),
            ctx.body(),
            finishedAt,
            startedAt,
            finishedAt,
            statusFor(false, ctx),
            cause.getMessage()));
  }

  private TransactionExecutionStatus statusFor(boolean success, Context<?> ctx) {
    if (ctx.mode() == ExecutionMode.COMPENSATION) {
      return success ? TransactionExecutionStatus.COMPENSATED : TransactionExecutionStatus.FAILED;
    }
    return success ? TransactionExecutionStatus.SUCCESS : TransactionExecutionStatus.FAILED;
  }
}

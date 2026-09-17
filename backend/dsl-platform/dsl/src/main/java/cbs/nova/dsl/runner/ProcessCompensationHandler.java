package cbs.nova.dsl.runner;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.exception.DslCompensationException;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionExecution;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class ProcessCompensationHandler {

  private final CompensationRegistry compensationRegistry;

  Result<?> compensateIfNeeded(
          ProcessDslObject process,
          Context<?> ctx,
          DefaultProcessRunner.ExecutionOutcome outcome,
          List<TransactionExecution> history) {
    if (outcome.result().isSuccess()) {
      return outcome.result();
    }
    if (!hasCompensationConfigured(process, ctx, history)) {
      return outcome.result();
    }
    return runCompensation(process, ctx, outcome, history);
  }

  private boolean hasCompensationConfigured(
          ProcessDslObject process, Context<?> ctx, List<TransactionExecution> history) {
    DslSaga saga = ctx.saga();
    return process.compensationLogic() != null
            || (!saga.isNoop() && saga.hasCompensations())
            || compensationRegistry.hasCompensation(ctx.runId())
            || (saga.isNoop() && !history.isEmpty());
  }

  private Result<?> runCompensation(
          ProcessDslObject process,
          Context<?> ctx,
          DefaultProcessRunner.ExecutionOutcome outcome,
          List<TransactionExecution> history) {
    var compensationError = resolveCompensationError(outcome);
    try {
      DslSaga saga = ctx.saga();
      if (!saga.isNoop() && saga.hasCompensations()) {
        saga.compensate();
      } else if (compensationRegistry.hasCompensation(ctx.runId())) {
        compensationRegistry.compensateAll(ctx.runId(), compensationError);
      } else {
        compensateTransactions(history, compensationError);
        compensateProcessHandler(process, ctx, compensationError, history);
      }
      return outcome.result();
    } catch (Exception compEx) {
      return Result.failure(new DslCompensationException(ctx.runId(), messageOf(compEx), compEx));
    }
  }

  private Throwable resolveCompensationError(DefaultProcessRunner.ExecutionOutcome outcome) {
    if (outcome.failure() != null) {
      return outcome.failure();
    }
    if (outcome.result().cause() != null) {
      return outcome.result().cause();
    }
    return new RuntimeException("compensation triggered");
  }

  private void compensateTransactions(
          List<TransactionExecution> history, Throwable compensationError) {
    for (TransactionExecution exec : history) {
      var input = exec.input() != null ? exec.input() : Map.of();
      var txCtx = SimpleContext.builder().body(input).mode(ExecutionMode.COMPENSATION)
              .runId(exec.runId()).build();
      GlobalManager.globalManager().compensateTransaction(exec.transactionName(), txCtx,
              compensationError);
    }
  }

  private void compensateProcessHandler(
          ProcessDslObject process,
          Context<?> ctx,
          Throwable compensationError,
          List<TransactionExecution> history) {
    if (process.compensationLogic() == null) {
      return;
    }
    var compCtxBase = SimpleContext.builder().body(ctx.body()).mode(ExecutionMode.COMPENSATION)
            .runId(ctx.runId()).build();
    var compCtx = GlobalManager.globalManager().createCompensationContext(compCtxBase,
            compensationError);
    process.compensationLogic().accept(compCtx, history);
  }

  private static String messageOf(Throwable ex) {
    return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
  }
}

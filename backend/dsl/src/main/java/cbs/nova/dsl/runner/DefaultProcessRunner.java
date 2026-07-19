package cbs.nova.dsl.runner;

import cbs.nova.dsl.CompensationRegistry;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslCompensationException;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionExecution;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessRichContext;
import cbs.nova.dsl.process.ProcessRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class DefaultProcessRunner implements ProcessRunner {

  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;
  private final CompensationRegistry compensationRegistry;

  private record ExecutionOutcome(Result<?> result, boolean launchedByTemporal, Throwable failure) {
  }

  @Override
  public @NonNull Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx) {
    var historyListener = new DefaultExecutionListener();
    var existingListener = ctx.executionListener();
    var listener = existingListener == null
            ? historyListener
            : new ChainedExecutionListener(existingListener, historyListener);
    var listeningCtx = ctx.withExecutionListener(listener);
    var outcome = execute(process, listeningCtx);
    if (outcome.launchedByTemporal()) {
      return outcome.result();
    }
    return compensateIfNeeded(process, ctx, outcome, historyListener.historyInReverse());
  }

  private ExecutionOutcome execute(ProcessDslObject process, Context<?> listeningCtx) {
    try {
      var launcher = resolveTemporalLauncher(listeningCtx);
      if (launcher != null) {
        return launchWithTemporal(process, launcher, listeningCtx);
      }
      return runDirectly(process, listeningCtx);
    } catch (Exception ex) {
      return new ExecutionOutcome(
              Result.failure(new DslExecutionException(listeningCtx.runId(), messageOf(ex), ex)),
              false,
              ex);
    }
  }

  private TemporalProcessLauncher resolveTemporalLauncher(@NonNull Context<?> ctx) {
    if (ctx.mode() == ExecutionMode.EXPLAIN || ctx.mode() == ExecutionMode.PREVIEW) {
      return null;
    }
    var launcher = DslConfig.dslConfig().temporalProcessLauncher().get();
    if (launcher == null || !launcher.canRun(ctx)) {
      return null;
    }
    return launcher;
  }

  private ExecutionOutcome launchWithTemporal(
          ProcessDslObject process, TemporalProcessLauncher launcher, Context<?> listeningCtx) {
    var result = launcher.launch(
            process.name(),
            process.taskQueue(),
            process.inputType(),
            process.outputType(),
            listeningCtx);
    return new ExecutionOutcome(result, true, null);
  }

  private ExecutionOutcome runDirectly(ProcessDslObject process, Context<?> listeningCtx) {
    var listener = listeningCtx.executionListener();
    var richCtx = new ProcessRichContext<>(listeningCtx, traceCollector, contextFactory);
    if (listener != null) {
      listener.onProcessStart(listeningCtx.runId(), process.name(), listeningCtx.body());
    }
    Result<?> result = null;
    try {
      if (listeningCtx.mode() == ExecutionMode.EXPLAIN) {
        result = process.executeLogic().apply(richCtx);
      } else if (listeningCtx.mode() == ExecutionMode.PREVIEW) {
        result = process.effectivePreview().apply(richCtx);
      } else {
        result = process.executeLogic().apply(richCtx);
      }
    } finally {
      if (listener != null) {
        listener.onProcessEnd(listeningCtx.runId(), process.name(),
                result != null ? result.value() : null, result != null && result.isSuccess());
      }
    }
    return new ExecutionOutcome(result, false, null);
  }

  private Result<?> compensateIfNeeded(
          ProcessDslObject process,
          Context<?> ctx,
          ExecutionOutcome outcome,
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
            || process.userCompensationHandler() != null
            || (saga != null && saga.hasCompensations())
            || compensationRegistry.hasCompensation(ctx.runId())
            || (saga == null && !history.isEmpty());
  }

  private Result<?> runCompensation(
          ProcessDslObject process,
          Context<?> ctx,
          ExecutionOutcome outcome,
          List<TransactionExecution> history) {
    var compensationError = resolveCompensationError(outcome);
    try {
      DslSaga saga = ctx.saga();
      if (saga != null && saga.hasCompensations()) {
        saga.compensate();
      } else if (compensationRegistry.hasCompensation(ctx.runId())) {
        compensationRegistry.compensateAll(ctx.runId(), compensationError, traceCollector,
                contextFactory);
      } else {
        compensateTransactions(history, compensationError);
        compensateProcessLogic(process, ctx, compensationError);
        compensateUserHandler(process, ctx, compensationError, history);
      }
      return outcome.result();
    } catch (Exception compEx) {
      return Result.failure(new DslCompensationException(ctx.runId(), messageOf(compEx), compEx));
    }
  }

  private Throwable resolveCompensationError(ExecutionOutcome outcome) {
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
      var txCtx = contextFactory.of(input, ExecutionMode.COMPENSATION, exec.runId());
      GlobalManager.globalManager().compensateTransaction(exec.transactionName(), txCtx,
              compensationError);
    }
  }

  private void compensateProcessLogic(
          ProcessDslObject process, Context<?> ctx, Throwable compensationError) {
    if (process.compensationLogic() == null) {
      return;
    }
    var processCompCtxBase = contextFactory.of(ctx.body(), ExecutionMode.COMPENSATION, ctx.runId());
    var compCtx = GlobalManager.globalManager().createCompensationContext(processCompCtxBase,
            compensationError);
    process.compensationLogic().apply(compCtx);
  }

  private void compensateUserHandler(
          ProcessDslObject process,
          Context<?> ctx,
          Throwable compensationError,
          List<TransactionExecution> history) {
    if (process.userCompensationHandler() == null) {
      return;
    }
    var userCompCtxBase = contextFactory.of(ctx.body(), ExecutionMode.COMPENSATION, ctx.runId());
    var userCompCtx = GlobalManager.globalManager().createCompensationContext(userCompCtxBase,
            compensationError);
    process.userCompensationHandler().accept(userCompCtx, history);
  }

  private static String messageOf(Throwable ex) {
    return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
  }
}

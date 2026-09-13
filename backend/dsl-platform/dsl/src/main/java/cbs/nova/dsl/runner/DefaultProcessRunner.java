package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.exception.DslCompensationException;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessRichContext;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionExecution;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class DefaultProcessRunner implements ProcessRunner {

  private final ContextFactory contextFactory;
  private final CompensationRegistry compensationRegistry;

  @Override
  public @NonNull Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx) {
    //TODO: instead change it to a class field, and pass on DI step in spring config class
    @Deprecated
    var repository = DslConfig.dslConfig().transactionExecutionRepository().get();
    //TODO: instead change it to a class field, and pass on DI step in spring config class
    @Deprecated
    var historyListener = new DefaultExecutionListener(ctx.runId(), repository);
    //TODO: instead change it to a class field, and pass on DI step in spring config class, only one listener
    @Deprecated
    var existingListener = ctx.executionListener();
    var listener = existingListener == null
            ? historyListener
            : new ChainedExecutionListener(existingListener, historyListener);
    //TODO: we always work with a `ExecutionListener`, redo
    @Deprecated
    var listeningCtx = ctx.withExecutionListener(listener);
    //TODO: extract a decorator, for a temporal execution
    @Deprecated
    var outcome = execute(process, listeningCtx);
    if (outcome.launchedByTemporal()) {
      return outcome.result();
    }
    return compensateIfNeeded(process, ctx, outcome, historyListener.historyInReverse());
  }

  private ExecutionOutcome execute(ProcessDslObject process, Context<?> listeningCtx) {
    try {
      //TODO: extract a decorator, for a temporal execution, it better to have some var in a metadata of context for that
      @Deprecated
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
    var richCtx = new ProcessRichContext<>(listeningCtx, contextFactory);
    if (listener != null) {
      //TODO: listener must be nonnull, remove useless if
      listener.onProcessStart(listeningCtx.runId(), process.name(), listeningCtx.body());
    }
    Result<?> result = null;
    try {
      //TODO: redo to a switch case, and redo a 3 different private methods
      if (listeningCtx.mode() == ExecutionMode.EXPLAIN) {
        result = process.explainLogic().apply(richCtx);
      } else if (listeningCtx.mode() == ExecutionMode.PREVIEW) {
        result = process.previewLogic().apply(richCtx);
      } else {
        result = process.executeLogic().apply(richCtx);
      }
    } finally {
      if (listener != null) {
        //TODO: listener must be nonnull, remove useless if
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
    //TODO: is better to set a Compensation as nonnull on dslObject step, and on builder step pass some NoOp lambda
    if (!hasCompensationConfigured(process, ctx, history)) {
      return outcome.result();
    }
    return runCompensation(process, ctx, outcome, history);
  }

  //TODO: from now, we always need a Compensation, by default it a NoOp
  @Deprecated
  private boolean hasCompensationConfigured(
          ProcessDslObject process, Context<?> ctx, List<TransactionExecution> history) {
    DslSaga saga = ctx.saga();
    return process.compensationLogic() != null
            || (saga != null && saga.hasCompensations())
            || compensationRegistry.hasCompensation(ctx.runId())
            || (saga == null && !history.isEmpty());
  }

  //TODO: extract another Class for a Compensation run
  @Deprecated
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
        compensationRegistry.compensateAll(ctx.runId(), compensationError, contextFactory);
      } else {
        compensateTransactions(history, compensationError);
        compensateProcessHandler(process, ctx, compensationError, history);
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

  private void compensateProcessHandler(
          ProcessDslObject process,
          Context<?> ctx,
          Throwable compensationError,
          List<TransactionExecution> history) {
    if (process.compensationLogic() == null) {
      return;
    }
    var compCtxBase = contextFactory.of(ctx.body(), ExecutionMode.COMPENSATION, ctx.runId());
    var compCtx = GlobalManager.globalManager().createCompensationContext(compCtxBase,
            compensationError);
    process.compensationLogic().accept(compCtx, history);
  }

  private static String messageOf(Throwable ex) {
    return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
  }

  private record ExecutionOutcome(Result<?> result, boolean launchedByTemporal, Throwable failure) {
  }

}

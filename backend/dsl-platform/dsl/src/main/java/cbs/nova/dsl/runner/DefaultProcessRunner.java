package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.listener.ChainedExecutionListener;
import cbs.nova.dsl.listener.DefaultExecutionListener;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessRichContext;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DefaultProcessRunner implements ProcessRunner {

  private final ContextFactory contextFactory;
  private final TransactionExecutionRepository transactionExecutionRepository;
  private final TemporalProcessLauncher temporalProcessLauncher;
  private final ProcessCompensationHandler compensationHandler;

  @Override
  public @NonNull Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx) {
    var historyListener = new DefaultExecutionListener(ctx.runId(), transactionExecutionRepository);
    var listener = new ChainedExecutionListener(ctx.executionListener(), historyListener);
    var listeningCtx = ctx.withExecutionListener(listener);
    var outcome = execute(process, listeningCtx);
    if (outcome.launchedByTemporal()) {
      return outcome.result();
    }
    return compensationHandler.compensateIfNeeded(process, ctx, outcome,
            historyListener.historyInReverse());
  }

  private ExecutionOutcome execute(ProcessDslObject process, Context<?> listeningCtx) {
    try {
      if (shouldUseTemporalLauncher(listeningCtx)) {
        return launchWithTemporal(process, listeningCtx);
      }
      return runDirectly(process, listeningCtx);
    } catch (Exception ex) {
      return new ExecutionOutcome(
              Result.failure(new DslExecutionException(listeningCtx.runId(), messageOf(ex), ex)),
              false,
              ex);
    }
  }

  private boolean shouldUseTemporalLauncher(@NonNull Context<?> ctx) {
    if (ctx.mode() == ExecutionMode.EXPLAIN || ctx.mode() == ExecutionMode.PREVIEW) {
      return false;
    }
    return temporalProcessLauncher != null && temporalProcessLauncher.canRun(ctx);
  }

  private ExecutionOutcome launchWithTemporal(
          ProcessDslObject process, Context<?> listeningCtx) {
    var result = temporalProcessLauncher.launch(
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
    listener.onProcessStart(listeningCtx.runId(), process.name(), listeningCtx.body());
    Result<?> result = null;
    try {
      result = switch (listeningCtx.mode()) {
        case EXPLAIN -> runExplainMode(process, richCtx);
        case PREVIEW -> runPreviewMode(process, richCtx);
        default -> runExecuteMode(process, richCtx);
      };
    } finally {
      listener.onProcessEnd(listeningCtx.runId(), process.name(),
              result != null ? result.value() : null, result != null && result.isSuccess());
    }
    return new ExecutionOutcome(result, false, null);
  }

  private Result<?> runExplainMode(ProcessDslObject process, ProcessRichContext<?> richCtx) {
    return process.explainLogic().apply(richCtx);
  }

  private Result<?> runPreviewMode(ProcessDslObject process, ProcessRichContext<?> richCtx) {
    return process.previewLogic().apply(richCtx);
  }

  private Result<?> runExecuteMode(ProcessDslObject process, ProcessRichContext<?> richCtx) {
    return process.executeLogic().apply(richCtx);
  }

  private static String messageOf(Throwable ex) {
    return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
  }

  static record ExecutionOutcome(Result<?> result, boolean launchedByTemporal, Throwable failure) {
  }

}

package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslCompensationException;
import cbs.nova.dsl.DslExecutionException;
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

import java.util.Map;

@RequiredArgsConstructor
public final class DefaultProcessRunner implements ProcessRunner {

  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;

  @Override
  public @NonNull Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx) {
    Result<?> result;
    Throwable failure = null;
    boolean launchedByTemporal = false;
    DefaultExecutionListener listener = new DefaultExecutionListener();
    Context<?> listeningCtx = ctx.withExecutionListener(listener);
    try {
      TemporalProcessLauncher launcher = DslConfig.dslConfig().temporalProcessLauncher().get();
      if (launcher != null && launcher.canRun(listeningCtx)) {
        launchedByTemporal = true;
        result = launcher.launch(
                process.name(),
                process.taskQueue(),
                process.inputType(),
                process.outputType(),
                listeningCtx);
      } else {
        var richCtx = new ProcessRichContext<>(listeningCtx, traceCollector, contextFactory);
        if (listeningCtx.mode() == ExecutionMode.EXPLAIN) {
          result = process.executeLogic().apply(richCtx);
          if (result.isSuccess()) {
            listeningCtx = listeningCtx.withMetadata("explain.description",
                    "Process: " + process.name());
          }
        } else if (listeningCtx.mode() == ExecutionMode.PREVIEW) {
          result = process.effectivePreview().apply(richCtx);
        } else {
          result = process.executeLogic().apply(richCtx);
        }
      }
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      result = Result.failure(new DslExecutionException(ctx.runId(), message, ex));
      failure = ex;
    }

    if (!launchedByTemporal && !result.isSuccess() && (process.compensationLogic() != null
            || process.userCompensationHandler() != null
            || !listener.historyInReverse().isEmpty())) {
      Throwable compensationError = failure != null
              ? failure
              : (result.cause() != null
                      ? result.cause()
                      : new RuntimeException("compensation triggered"));
      try {
        var reverseHistory = listener.historyInReverse();
        for (TransactionExecution exec : reverseHistory) {
          var txCtx = contextFactory.of(
                  exec.input() != null ? exec.input() : Map.of(),
                  ExecutionMode.COMPENSATION,
                  exec.runId());
          GlobalManager.getInstance().compensateTransaction(exec.transactionName(), txCtx,
                  compensationError);
        }
        if (process.compensationLogic() != null) {
          var processCompCtxBase = contextFactory.of(
                  ctx.body(), ExecutionMode.COMPENSATION, ctx.runId());
          process.compensationLogic().apply(
                  GlobalManager.getInstance().createCompensationContext(processCompCtxBase,
                          compensationError));
        }
        if (process.userCompensationHandler() != null) {
          var userCompCtxBase = contextFactory.of(
                  ctx.body(), ExecutionMode.COMPENSATION, ctx.runId());
          process.userCompensationHandler().accept(
                  GlobalManager.getInstance().createCompensationContext(userCompCtxBase,
                          compensationError),
                  reverseHistory);
        }
      } catch (Exception compEx) {
        String message = compEx.getMessage() != null
                ? compEx.getMessage()
                : compEx.getClass().getSimpleName();
        return Result.failure(new DslCompensationException(ctx.runId(), message, compEx));
      }
    }
    return result;
  }
}

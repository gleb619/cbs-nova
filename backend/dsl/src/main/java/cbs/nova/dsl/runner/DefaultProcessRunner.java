package cbs.nova.dsl.runner;

import cbs.nova.dsl.CompensationRichContext;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslCompensationException;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessRichContext;
import cbs.nova.dsl.process.ProcessRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DefaultProcessRunner implements ProcessRunner {

  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;

  @Override
  public @NonNull Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx) {
    Result<?> result;
    Throwable failure = null;
    try {
      var richCtx = new ProcessRichContext<>(ctx, traceCollector, contextFactory);
      if (ctx.mode() == ExecutionMode.EXPLAIN) {
        result = process.executeLogic().apply(richCtx);
        if (result.isSuccess()) {
          ctx = ctx.withMetadata("explain.description", "Process: " + process.name());
        }
      } else if (ctx.mode() == ExecutionMode.PREVIEW) {
        result = process.effectivePreview().apply(richCtx);
      } else {
        // RUN: TODO wire to Temporal
        result = process.executeLogic().apply(richCtx);
      }
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      result = Result.failure(new DslExecutionException(ctx.runId(), message, ex));
      failure = ex;
    }
    if (!result.isSuccess() && process.compensationLogic() != null) {
      try {
        var compensationCtx = new CompensationRichContext<>(ctx,
                failure != null ? failure : result.cause(), traceCollector, contextFactory);
        process.compensationLogic().apply(compensationCtx);
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

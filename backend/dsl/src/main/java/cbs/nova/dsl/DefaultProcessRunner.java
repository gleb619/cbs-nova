package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class DefaultProcessRunner implements ProcessRunner {

  @Override
  public @NonNull Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx) {
    Result<?> result;
    Throwable failure = null;
    try {
      var richCtx = new ProcessRichContext<>(ctx);
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
                failure != null ? failure : result.cause());
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

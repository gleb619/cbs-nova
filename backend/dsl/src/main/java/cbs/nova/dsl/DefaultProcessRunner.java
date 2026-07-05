package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class DefaultProcessRunner implements ProcessRunner {

  @Override
  public @NonNull Result<?> run(@NonNull ProcessDslObject process, @NonNull Context<?> ctx) {
    Result<?> result;
    try {
      if (ctx.mode() == ExecutionMode.EXPLAIN) {
        result = process.executeLogic().apply(ctx);
        if (result.isSuccess()) {
          ctx = ctx.withMetadata("explain.description", "Process: " + process.name());
        }
      } else {
        // TODO: wire to Temporal in RUN mode
        result = process.executeLogic().apply(ctx);
      }
    } catch (Exception ex) {
      result = Result.failure(ex);
    }
    if (!result.isSuccess() && process.compensationLogic() != null) {
      try {
        process.compensationLogic().apply(ctx);
      } catch (Exception ignored) {
      }
    }
    return result;
  }
}

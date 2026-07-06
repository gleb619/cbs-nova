package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class DefaultHelperRunner implements HelperRunner {

  @Override
  @SuppressWarnings("unchecked")
  public @NonNull Result<?> runHelper(
          @NonNull String name, @NonNull Context<?> ctx, @NonNull HelperRegistry registry) {
    var helper = registry.findHelper(name);
    if (helper.isEmpty()) {
      return Result
              .failure(new DslEntityNotFoundException(ctx.runId(), "Helper not found: " + name));
    }
    try {
      var cast = (Executable<Object, Object>) helper.get();
      var result = ctx.mode() == ExecutionMode.PREVIEW
              ? cast.preview((Context<Object>) ctx)
              : cast.execute((Context<Object>) ctx);
      return result;
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      return Result.failure(new DslExecutionException(ctx.runId(), message, ex));
    }
  }

  @Override
  public @NonNull Result<?> runFunction(
          @NonNull String name, @NonNull Context<?> ctx, @NonNull HelperRegistry registry) {
    var fn = registry.findFunction(name);
    if (fn.isEmpty()) {
      return Result
              .failure(new DslEntityNotFoundException(ctx.runId(), "Function not found: " + name));
    }
    try {
      var richCtx = new FunctionRichContext<>(ctx);
      return fn.get().executeLogic().apply(richCtx);
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      return Result.failure(new DslExecutionException(ctx.runId(), message, ex));
    }
  }
}

package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslEntityNotFoundException;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.function.FunctionRichContext;
import cbs.nova.dsl.registry.HelperRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.function.Function;

@RequiredArgsConstructor
public final class DefaultHelperRunner implements HelperRunner {

  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;

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
      var richCtx = new FunctionRichContext<>(ctx, traceCollector, contextFactory);
      Function<FunctionContext<?>, Result<?>> logic = ctx.mode() == ExecutionMode.PREVIEW
              ? fn.get().effectivePreview()
              : fn.get().executeLogic();
      return logic.apply(richCtx);
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      return Result.failure(new DslExecutionException(ctx.runId(), message, ex));
    }
  }
}

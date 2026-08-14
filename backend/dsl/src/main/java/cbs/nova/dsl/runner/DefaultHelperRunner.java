package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
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
    var listener = ctx.executionListener();
    if (listener != null) {
      listener.onHelperStart(ctx.runId(), name, ctx.body());
    }
    Result<?> result = null;
    try {
      var cast = (Executable<Object, Object>) helper.get();
      result = ctx.mode() == ExecutionMode.PREVIEW
              ? cast.preview((Context<Object>) ctx)
              : cast.execute((Context<Object>) ctx);
      return result;
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      result = Result.failure(new DslExecutionException(ctx.runId(), message, ex));
      return result;
    } finally {
      if (listener != null) {
        listener.onHelperEnd(ctx.runId(), name,
                result != null ? result.value() : null, result != null && result.isSuccess());
      }
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
    var listener = ctx.executionListener();
    if (listener != null) {
      listener.onFunctionStart(ctx.runId(), name, ctx.body());
    }
    Result<?> result = null;
    try {
      var richCtx = new FunctionRichContext<>(ctx, contextFactory);
      Function<FunctionContext<?>, Result<?>> logic = ctx.mode() == ExecutionMode.PREVIEW
              ? fn.get().effectivePreview()
              : fn.get().executeLogic();
      result = logic.apply(richCtx);
      return result;
    } catch (Exception ex) {
      String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
      result = Result.failure(new DslExecutionException(ctx.runId(), message, ex));
      return result;
    } finally {
      if (listener != null) {
        listener.onFunctionEnd(ctx.runId(), name,
                result != null ? result.value() : null, result != null && result.isSuccess());
      }
    }
  }
}

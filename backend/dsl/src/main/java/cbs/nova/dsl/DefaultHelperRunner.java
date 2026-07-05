package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class DefaultHelperRunner implements HelperRunner {

  @Override
  @SuppressWarnings("unchecked")
  public @NonNull Result<?> runHelper(
          @NonNull String name, @NonNull Context<?> ctx, @NonNull HelperRegistry registry) {
    var helper = registry.findHelper(name);
    if (helper.isEmpty()) {
      return Result.failure(new IllegalArgumentException("Helper not found: " + name));
    }
    try {
      return ((Executable<Object, Object>) helper.get()).execute((Context<Object>) ctx);
    } catch (Exception ex) {
      return Result.failure(ex);
    }
  }

  @Override
  public @NonNull Result<?> runFunction(
          @NonNull String name, @NonNull Context<?> ctx, @NonNull HelperRegistry registry) {
    var fn = registry.findFunction(name);
    if (fn.isEmpty()) {
      return Result.failure(new IllegalArgumentException("Function not found: " + name));
    }
    try {
      var richCtx = new FunctionRichContext<>(ctx);
      return fn.get().executeLogic().apply(richCtx);
    } catch (Exception ex) {
      return Result.failure(ex);
    }
  }
}

package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.helper.HelperRegistrar;
import cbs.nova.dsl.registry.HelperRegistry;
import cbs.nova.dsl.runner.HelperRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@RequiredArgsConstructor
public final class HelperManager implements HelperRegistrar {

  private final HelperRegistry registry;
  private final HelperRunner runner;

  @Override
  public void register(@NonNull String name, @NonNull Executable<?, ?> helper) {
    registry.registerHelper(name, helper);
  }

  @Override
  public void register(@NonNull String name, @NonNull Supplier<Executable<?, ?>> helperSupplier) {
    registry.registerHelper(name, helperSupplier);
  }

  public void registerFunction(@NonNull FunctionDslObject fn) {
    registry.registerFunction(fn);
  }

  public @NonNull Result<?> executeHelper(@NonNull String name, @NonNull Context<?> ctx) {
    HelperInterceptor interceptor = ctx.helperInterceptor();
    if (interceptor != null) {
      Optional<Result<?>> fake = interceptor.intercept(name, ctx);
      if (fake.isPresent()) {
        return fake.get();
      }
    }
    return runner.runHelper(name, ctx, registry);
  }

  public @NonNull Result<?> executeFunction(@NonNull String name, @NonNull Context<?> ctx) {
    HelperInterceptor interceptor = ctx.helperInterceptor();
    if (interceptor != null) {
      Optional<Result<?>> fake = interceptor.intercept(name, ctx);
      if (fake.isPresent()) {
        return fake.get();
      }
    }
    return runner.runFunction(name, ctx, registry);
  }

  public boolean contains(@NonNull String name) {
    return registry.containsName(name);
  }

  public @NonNull Optional<Executable<?, ?>> findHelper(@NonNull String name) {
    return registry.findHelper(name);
  }

  public @NonNull Optional<FunctionDslObject> findFunction(@NonNull String name) {
    return registry.findFunction(name);
  }

  public @NonNull List<String> names() {
    return registry.allNames().stream().sorted().toList();
  }
}

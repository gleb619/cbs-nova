package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public final class HelperManager {
  private final HelperRegistry registry;
  private final HelperRunner runner;

  public void registerHelper(@NonNull String name, @NonNull Executable<?, ?> helper) {
    registry.registerHelper(name, helper);
  }

  public void registerFunction(@NonNull FunctionDslObject fn) {
    registry.registerFunction(fn);
  }

  public @NonNull Result<?> executeHelper(@NonNull String name, @NonNull Context<?> ctx) {
    return runner.runHelper(name, ctx, registry);
  }

  public @NonNull Result<?> executeFunction(@NonNull String name, @NonNull Context<?> ctx) {
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

package cbs.nova.dsl.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.registry.HelperRegistry;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.security.ObjectGuard;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

public final class HelperManager implements HelperRegistrar {

  private final HelperRegistry registry;
  private final HelperRunner runner;
  private final ObjectGuard objectGuard;

  public HelperManager(@NonNull HelperRegistry registry, @NonNull HelperRunner runner) {
    this(registry, runner, DslConfig.dslConfig().objectGuard().get());
  }

  public HelperManager(@NonNull HelperRegistry registry, @NonNull HelperRunner runner,
          @NonNull ObjectGuard objectGuard) {
    this.registry = Objects.requireNonNull(registry, "registry required");
    this.runner = Objects.requireNonNull(runner, "runner required");
    this.objectGuard = Objects.requireNonNull(objectGuard, "objectGuard required");
  }

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
    Optional<ObjectGuard.Denial> denial = objectGuard.check(
            ctx.mode(), definitionName(ctx), "helper", name, correlationId(ctx));
    if (denial.isPresent()) {
      return Result.failure(capabilityDeniedException(denial.get()));
    }
    HelperInterceptor interceptor = ctx.helperInterceptor();
    Optional<Result<?>> fake = interceptor.intercept(name, ctx);
    if (fake.isPresent()) {
      return fake.get();
    }
    return runner.runHelper(name, ctx, registry);
  }

  public @NonNull Result<?> executeFunction(@NonNull String name, @NonNull Context<?> ctx) {
    Optional<ObjectGuard.Denial> denial = objectGuard.check(
            ctx.mode(), definitionName(ctx), "function", name, correlationId(ctx));
    if (denial.isPresent()) {
      return Result.failure(capabilityDeniedException(denial.get()));
    }
    HelperInterceptor interceptor = ctx.helperInterceptor();
    Optional<Result<?>> fake = interceptor.intercept(name, ctx);
    if (fake.isPresent()) {
      return fake.get();
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

  private @NonNull String definitionName(@NonNull Context<?> ctx) {
    Object value = ctx.metadata().get(Constants.DSL_DEFINITION_NAME_METADATA_KEY);
    return value instanceof String s && !s.isBlank() ? s : "";
  }

  private @NonNull String correlationId(@NonNull Context<?> ctx) {
    Object value = ctx.metadata().get(Constants.CORRELATION_ID_METADATA_KEY);
    return value instanceof String s && !s.isBlank() ? s : null;
  }

  private static @NonNull RuntimeException capabilityDeniedException(
          ObjectGuard.@NonNull Denial denial) {
    return new RuntimeException(String.format(
            "Capability denied: %s:%s piece=%s reason=%s",
            denial.objectType(), denial.objectName(),
            denial.pieceId() != null ? denial.pieceId() : "-",
            denial.reason()));
  }
}

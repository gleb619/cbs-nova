package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public record FunctionDslObject(
        @NonNull String name,
        @Nullable List<ParameterDescriptor> parameters,
        @NonNull Function<FunctionContext<?>, Result<?>> executeLogic) implements DslObject {
  @Override
  public @NonNull DslType type() {
    return DslType.FUNCTION;
  }
}

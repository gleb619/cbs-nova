package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.function.Function;

public record FunctionDslObject(
        @NonNull String name,
        @NonNull Function<Context<?>, Result<?>> executeLogic) implements DslObject {
  @Override
  public @NonNull DslType type() {
    return DslType.FUNCTION;
  }
}

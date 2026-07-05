package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public record ProcessDslObject(
        @NonNull String name,
        @NonNull String taskQueue,
        @NonNull String version,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @Nullable List<ParameterDescriptor> parameters,
        @NonNull Function<ProcessContext<?>, Result<?>> executeLogic,
        @Nullable Function<CompensationContext<?>, Result<?>> compensationLogic)
        implements
          DslObject {
  @Override
  public @NonNull DslType type() {
    return DslType.PROCESS;
  }
}

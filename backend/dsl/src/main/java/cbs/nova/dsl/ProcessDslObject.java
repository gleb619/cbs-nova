package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public record ProcessDslObject(
        @NonNull String name,
        @NonNull String taskQueue,
        @NonNull String version,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @Nullable List<ParameterDescriptor> parameters,
        @NonNull Function<ProcessContext<?>, Result<?>> executeLogic,
        @Nullable Function<CompensationContext<?>, Result<?>> compensationLogic,
        @Nullable Function<ProcessContext<?>, Result<?>> previewLogic,
        @Nullable Supplier<DslDescriptor> descriptor)
        implements
          DslObject {
  @Override
  public @NonNull DslType type() {
    return DslType.PROCESS;
  }

  public @NonNull Function<ProcessContext<?>, Result<?>> effectivePreview() {
    return previewLogic != null ? previewLogic : executeLogic;
  }

  public @NonNull DslDescriptor describe() {
    if (descriptor != null)
      return descriptor.get();
    return new DslDescriptor(
            name,
            DslType.PROCESS,
            null,
            inputType,
            outputType,
            compensationLogic != null,
            true,
            "delegates to execute",
            parameters != null ? parameters : List.of(),
            taskQueue,
            version,
            null,
            null);
  }
}

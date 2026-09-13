package cbs.nova.dsl.process;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
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
        @NonNull Function<ProcessContext<?>, Result<ExplainReport>> explainLogic,
        @Nullable Supplier<DslDescriptor> descriptor,
        @Nullable BiConsumer<CompensationContext<?>, List<TransactionExecution>> userCompensationHandler,
        @Nullable String description) implements DslObject {

  @Override
  public @NonNull DslType type() {
    return DslType.PROCESS;
  }

  public @NonNull Function<ProcessContext<?>, Result<?>> effectivePreview() {
    return previewLogic != null ? previewLogic : executeLogic;
  }

  public @NonNull Function<ProcessContext<?>, Result<ExplainReport>> effectiveExplain() {
    return explainLogic;
  }

  public @NonNull DslDescriptor describe() {
    if (descriptor != null) {
      return descriptor.get();
    }
    return defaultDescriptor(name, taskQueue, version, inputType, outputType, parameters,
            compensationLogic != null, description);
  }

  public static @NonNull DslDescriptor defaultDescriptor(
          @NonNull String name,
          @NonNull String taskQueue,
          @NonNull String version,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @Nullable List<ParameterDescriptor> parameters,
          boolean hasCompensation,
          @Nullable String description) {
    return DslDescriptor.builder()
            .name(name)
            .type(DslType.PROCESS)
            .description(description)
            .inputType(inputType)
            .outputType(outputType)
            .hasCompensation(hasCompensation)
            .hasSideEffects(true)
            .parameters(parameters != null ? parameters : List.of())
            .taskQueue(taskQueue)
            .version(version)
            .startToCloseTimeout(null)
            .heartbeatTimeout(null)
            .build();
  }
}

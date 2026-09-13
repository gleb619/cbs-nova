package cbs.nova.dsl.function;

import static cbs.nova.dsl.config.DslConstants.DEFAULT_HEARTBEAT_TIMEOUT;
import static cbs.nova.dsl.config.DslConstants.DEFAULT_START_TO_CLOSE_TIMEOUT;
import static cbs.nova.dsl.config.DslConstants.DEFAULT_TASK_QUEUE;
import static cbs.nova.dsl.config.DslConstants.DEFAULT_VERSION;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public record FunctionDslObject(
        @NonNull String name,
        @Nullable List<ParameterDescriptor> parameters,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @NonNull Function<FunctionContext<?>, Result<?>> executeLogic,
        @Nullable Function<FunctionContext<?>, Result<?>> previewLogic,
        @NonNull Function<FunctionContext<?>, Result<ExplainReport>> explainLogic,
        @Nullable Supplier<DslDescriptor> descriptor,
        @Nullable String description) implements DslObject {

  @Override
  public @NonNull DslType type() {
    return DslType.FUNCTION;
  }

  public @NonNull Function<FunctionContext<?>, Result<?>> effectivePreview() {
    return previewLogic != null ? previewLogic : executeLogic;
  }

  public @NonNull Function<FunctionContext<?>, Result<ExplainReport>> effectiveExplain() {
    return explainLogic;
  }

  public @NonNull DslDescriptor describe() {
    if (descriptor != null) {
      return descriptor.get();
    }
    return defaultDescriptor(name, parameters, inputType, outputType, description);
  }

  public static @NonNull DslDescriptor defaultDescriptor(
          @NonNull String name,
          @Nullable List<ParameterDescriptor> parameters,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @Nullable String description) {
    return DslDescriptor.builder()
            .name(name)
            .type(DslType.FUNCTION)
            .description(description)
            .inputType(inputType)
            .outputType(outputType)
            .hasCompensation(false)
            .hasSideEffects(false)
            .parameters(parameters != null ? parameters : List.of())
            .taskQueue(DEFAULT_TASK_QUEUE)
            .version(DEFAULT_VERSION)
            .startToCloseTimeout(DEFAULT_START_TO_CLOSE_TIMEOUT)
            .heartbeatTimeout(DEFAULT_HEARTBEAT_TIMEOUT)
            .build();
  }
}

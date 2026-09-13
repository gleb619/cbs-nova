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
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Builder
public record FunctionDslObject(
        @NonNull String name,
        @NonNull List<ParameterDescriptor> parameters,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @NonNull Function<FunctionContext<?>, Result<?>> executeLogic,
        @NonNull Function<FunctionContext<?>, Result<?>> previewLogic,
        @NonNull Function<FunctionContext<?>, Result<ExplainReport>> explainLogic,
        @NonNull Supplier<DslDescriptor> descriptor,
        @Nullable String description) implements DslObject {

  @Override
  public @NonNull DslType type() {
    return DslType.FUNCTION;
  }

  public @NonNull Function<FunctionContext<?>, Result<?>> effectivePreview() {
    return previewLogic;
  }

  public @NonNull Function<FunctionContext<?>, Result<ExplainReport>> effectiveExplain() {
    return explainLogic;
  }

  public @NonNull DslDescriptor describe() {
    return descriptor.get();
  }

  public static @NonNull DslDescriptor defaultDescriptor(
          @NonNull String name,
          @NonNull List<ParameterDescriptor> parameters,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @Nullable String description) {
    return DslDescriptor.builder()
            .name(name)
            .type(DslType.FUNCTION)
            .description(description)
            .inputType(inputType)
            .outputType(outputType)
            .hasSideEffects(false)
            .parameters(parameters)
            .taskQueue(DEFAULT_TASK_QUEUE)
            .version(DEFAULT_VERSION)
            .startToCloseTimeout(DEFAULT_START_TO_CLOSE_TIMEOUT)
            .heartbeatTimeout(DEFAULT_HEARTBEAT_TIMEOUT)
            .build();
  }
}

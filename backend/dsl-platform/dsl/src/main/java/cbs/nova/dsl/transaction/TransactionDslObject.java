package cbs.nova.dsl.transaction;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.RetryPolicy;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Builder
public record TransactionDslObject(
        @NonNull String name,
        @NonNull String taskQueue,
        @NonNull String version,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @NonNull List<ParameterDescriptor> parameters,
        @NonNull Function<TransactionContext<?>, Result<?>> executeLogic,
        @Nullable Function<CompensationContext<?>, Result<?>> compensationLogic,
        @NonNull Duration startToCloseTimeout,
        @Nullable RetryPolicy retryPolicy,
        @Nullable Duration heartbeatTimeout,
        @NonNull Function<TransactionContext<?>, Result<?>> previewLogic,
        @NonNull Function<TransactionContext<?>, Result<ExplainReport>> explainLogic,
        @NonNull Supplier<DslDescriptor> descriptor,
        @Nullable String description) implements DslObject {

  @Override
  public @NonNull DslType type() {
    return DslType.TRANSACTION;
  }

  public @NonNull Function<TransactionContext<?>, Result<?>> effectivePreview() {
    return previewLogic;
  }

  public @NonNull Function<TransactionContext<?>, Result<ExplainReport>> effectiveExplain() {
    return explainLogic;
  }

  public @NonNull DslDescriptor describe() {
    return descriptor.get();
  }

  public static @NonNull DslDescriptor defaultDescriptor(
          @NonNull String name,
          @NonNull String taskQueue,
          @NonNull String version,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @NonNull List<ParameterDescriptor> parameters,
          boolean hasCompensation,
          @NonNull Duration startToCloseTimeout,
          @Nullable RetryPolicy retryPolicy,
          @Nullable Duration heartbeatTimeout,
          @Nullable String description) {
    return DslDescriptor.builder()
            .name(name)
            .type(DslType.TRANSACTION)
            .description(description)
            .inputType(inputType)
            .outputType(outputType)
            .hasSideEffects(true)
            .parameters(parameters)
            .taskQueue(taskQueue)
            .version(version)
            .startToCloseTimeout(startToCloseTimeout)
            .heartbeatTimeout(heartbeatTimeout)
            .build();
  }
}

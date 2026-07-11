package cbs.nova.dsl.transaction;

import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.RetryPolicy;
import cbs.nova.dsl.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public record TransactionDslObject(
        @NonNull String name,
        @NonNull String taskQueue,
        @NonNull String version,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        @Nullable List<ParameterDescriptor> parameters,
        @NonNull Function<TransactionContext<?>, Result<?>> executeLogic,
        @Nullable Function<CompensationContext<?>, Result<?>> compensationLogic,
        @NonNull Duration startToCloseTimeout,
        @Nullable RetryPolicy retryPolicy,
        @Nullable Duration heartbeatTimeout,
        @Nullable Function<TransactionContext<?>, Result<?>> previewLogic,
        @Nullable Supplier<DslDescriptor> descriptor) implements DslObject {

  @Override
  public @NonNull DslType type() {
    return DslType.TRANSACTION;
  }

  public @NonNull Function<TransactionContext<?>, Result<?>> effectivePreview() {
    return previewLogic != null ? previewLogic : executeLogic;
  }

  public @NonNull DslDescriptor describe() {
    if (descriptor != null) {
      return descriptor.get();
    }
    return new DslDescriptor(
            name,
            DslType.TRANSACTION,
            null,
            inputType,
            outputType,
            compensationLogic != null,
            true,
            "delegates to execute",
            parameters != null ? parameters : List.of(),
            taskQueue,
            version,
            startToCloseTimeout,
            heartbeatTimeout);
  }
}

package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

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
        @Nullable Duration heartbeatTimeout) implements DslObject {
  @Override
  public @NonNull DslType type() {
    return DslType.TRANSACTION;
  }
}

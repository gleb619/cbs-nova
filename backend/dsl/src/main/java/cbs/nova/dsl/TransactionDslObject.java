package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.function.Function;

public record TransactionDslObject(
        @NonNull String name,
        @NonNull String taskQueue,
        @NonNull String version,
        @NonNull Class<?> inputType,
        @NonNull Class<?> outputType,
        @NonNull Function<Context<?>, Result<?>> executeLogic,
        @Nullable Function<Context<?>, Result<?>> compensationLogic,
        @NonNull Duration startToCloseTimeout,
        @Nullable RetryPolicy retryPolicy) implements DslObject {
  @Override
  public @NonNull DslType type() {
    return DslType.TRANSACTION;
  }
}

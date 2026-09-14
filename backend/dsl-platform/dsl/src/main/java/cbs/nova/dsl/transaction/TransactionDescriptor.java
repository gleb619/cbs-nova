package cbs.nova.dsl.transaction;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.model.ObjectDescriptor;
import cbs.nova.dsl.model.RetryPolicy;
import java.time.Duration;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
public record TransactionDescriptor(
        @NonNull String name,
        @Nullable String description,
        @NonNull String version,
        @NonNull String taskQueue,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasCompensation,
        @NonNull List<String> helperRefs,
        @NonNull Duration startToCloseTimeout,
        @Nullable RetryPolicy retryPolicy,
        @Nullable Duration heartbeatTimeout) implements ObjectDescriptor {

  @Override
  public DslType type() {
    return DslType.TRANSACTION;
  }

}

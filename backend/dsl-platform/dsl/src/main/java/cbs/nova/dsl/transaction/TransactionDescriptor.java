package cbs.nova.dsl.transaction;

import cbs.nova.dsl.model.ObjectDescriptor;
import cbs.nova.dsl.model.RetryPolicy;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@Builder
public record TransactionDescriptor(
        @NonNull String name,
        @NonNull String version,
        @NonNull String taskQueue,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasCompensation,
        @NonNull List<String> helperRefs,
        @NonNull Duration startToCloseTimeout,
        @Nullable RetryPolicy retryPolicy,
        @Nullable Duration heartbeatTimeout) implements ObjectDescriptor {

}

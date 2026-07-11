package cbs.nova.dsl.transaction;

import cbs.nova.dsl.RetryPolicy;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
        @Nullable Duration heartbeatTimeout) {
}

package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public record TransactionDescriptor(
        @NonNull String name,
        @NonNull String version,
        @NonNull String taskQueue,
        @Nullable Class<?> inputType,
        @Nullable Class<?> outputType,
        boolean hasCompensation,
        @NonNull List<String> helperRefs,
        @NonNull Duration startToCloseTimeout,
        @Nullable RetryPolicy retryPolicy) {
}

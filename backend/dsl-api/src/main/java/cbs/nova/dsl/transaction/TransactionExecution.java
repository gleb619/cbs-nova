package cbs.nova.dsl.transaction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record TransactionExecution(
        @NonNull String runId,
        @NonNull String transactionName,
        @Nullable Object input,
        @NonNull Instant executedAt) {
}

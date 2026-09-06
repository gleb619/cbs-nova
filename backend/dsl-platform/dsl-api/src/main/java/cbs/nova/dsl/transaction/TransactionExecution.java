package cbs.nova.dsl.transaction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record TransactionExecution(
        @NonNull String runId,
        @NonNull String transactionName,
        @Nullable Object input,
        @NonNull Instant executedAt,
        @NonNull Instant startedAt,
        @Nullable Instant finishedAt,
        @NonNull TransactionExecutionStatus status,
        @Nullable String error) {

  public long durationMillis() {
    return finishedAt == null
            ? 0
            : Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
  }
}

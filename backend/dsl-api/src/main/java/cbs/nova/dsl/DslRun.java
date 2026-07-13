package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Record of a single DSL process run.
 */
public record DslRun(
        @NonNull String runId,
        @NonNull String processName,
        @NonNull String status,
        @Nullable String input,
        @Nullable String output,
        @Nullable String error,
        @NonNull Instant startedAt,
        @Nullable Instant finishedAt,
        @Nullable String executionMode) {
}

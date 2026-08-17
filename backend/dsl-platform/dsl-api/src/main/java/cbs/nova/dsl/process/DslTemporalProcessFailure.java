package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;

public record DslTemporalProcessFailure(
        @NonNull String message,
        @NonNull String detail) {
}

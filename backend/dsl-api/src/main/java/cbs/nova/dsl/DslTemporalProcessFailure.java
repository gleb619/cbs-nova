package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * Sentinel value returned by a generated Temporal workflow when the DSL process fails. Returning
 * this marker instead of throwing lets the workflow complete immediately while still allowing the
 * caller to distinguish failure from success.
 */
public record DslTemporalProcessFailure(
        @NonNull String message,
        @NonNull String detail) {
}

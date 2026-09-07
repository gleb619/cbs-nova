package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A single semantic validation issue with a stable {@link #code} and a human-readable
 * {@link #message}. See {@link DiagnosticCodes} for the registry of semantic codes.
 */
public record ValidationIssue(@Nullable String code, @NonNull String message) {

}

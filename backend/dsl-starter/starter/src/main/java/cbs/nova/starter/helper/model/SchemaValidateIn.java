package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code schemaValidate} helper.
 *
 * <p>
 * {@code payload} is the JSON string to validate. {@code schema} is a JSON Schema object string
 * describing the expected structure. Both fields are required and must be non-blank.
 * {@code failFast} (default {@code false}) selects fail-on-first vs collect-all-errors: when
 * {@code true}, validation stops after the first reported error; when {@code false} (default), all
 * matching violations are reported in a single call.
 */
public record SchemaValidateIn(
        @Nullable String payload,
        @Nullable String schema,
        @Nullable Boolean failFast) {
}

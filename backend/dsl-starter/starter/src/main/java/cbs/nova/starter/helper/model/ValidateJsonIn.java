package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code validateJson} helper.
 *
 * <p>
 * {@code payload} is the JSON string to validate. {@code schema} is a JSON Schema object string
 * defining the expected structure. Both fields are required and must be non-blank.
 */
public record ValidateJsonIn(String payload, String schema) {
}

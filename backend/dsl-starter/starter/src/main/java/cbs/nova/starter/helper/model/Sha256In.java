package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code sha256} helper.
 *
 * <p>
 * {@code input} is the string to hash (required but may be empty). {@code encoding} defaults to
 * {@code "hex"} when null/blank and must be one of {@code hex}, {@code base64}, or
 * {@code base64url} (case-insensitive).
 */
public record Sha256In(String input, String encoding) {
}

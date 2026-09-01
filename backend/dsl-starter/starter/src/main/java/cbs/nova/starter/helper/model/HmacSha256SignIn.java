package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code hmacSha256Sign} helper.
 *
 * <p>
 * {@code message} is the data to sign. Empty string is allowed. {@code secret} is required and must
 * not be empty. {@code encoding} defaults to {@code "hex"} when null/blank and must be one of
 * {@code hex}, {@code base64}, or {@code base64url} (case-insensitive).
 */
public record HmacSha256SignIn(String message, String secret, String encoding) {
}

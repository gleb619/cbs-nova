package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code base64} helper.
 *
 * <p>
 * {@code mode} must be {@code "encode"} or {@code "decode"} (case-insensitive). {@code urlSafe}
 * defaults to {@code false} when null; when true the URL-safe Base64 alphabet ({@code -_}) is used
 * instead of the standard {@code +/} alphabet.
 */
public record Base64In(String input, String mode, Boolean urlSafe) {
}

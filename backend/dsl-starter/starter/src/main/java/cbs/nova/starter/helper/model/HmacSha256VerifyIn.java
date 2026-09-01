package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code hmacSha256Verify} helper.
 *
 * <p>
 * {@code message}, {@code secret}, and {@code signature} are required. {@code signature} is decoded
 * with the same {@code encoding} used to produce the expected signature. {@code encoding} defaults
 * to {@code "hex"} when null/blank and must be one of {@code hex}, {@code base64}, or
 * {@code base64url} (case-insensitive).
 */
public record HmacSha256VerifyIn(String message, String secret, String signature, String encoding) {
}

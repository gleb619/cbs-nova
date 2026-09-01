package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code hmacSha256Sign} helper.
 *
 * <p>
 * {@code signature} contains the computed HMAC-SHA256 in the requested encoding. {@code encoding}
 * echoes the normalized encoding name that was used.
 */
public record HmacSha256SignOut(String signature, String encoding) {
}

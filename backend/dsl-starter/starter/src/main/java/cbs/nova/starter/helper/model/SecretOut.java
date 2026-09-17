package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code secret} helper.
 *
 * <p>
 * {@code result} is the encoded random value as a {@link String}: hexadecimal, Base64, or Base64url
 * for {@code "bytes"} (per the requested {@code encoding}), or the URL-safe token string for
 * {@code "token"}.
 */
public record SecretOut(String result) {
}

package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code secret} helper.
 *
 * <p>
 * Only the fields required by the selected {@code mode} are used; the remaining fields may be
 * {@code null}:
 * <ul>
 * <li>{@code "bytes"} requires {@code length} (must be in {@code [0, 100000]}); {@code encoding}
 * selects the output encoding — {@code "hex"} (default), {@code "base64"}, or
 * {@code "base64url"}.</li>
 * <li>{@code "token"} requires {@code length} (must be in {@code [0, 100000]}) and produces a
 * URL-safe opaque string of that many characters; {@code encoding} is unused.</li>
 * </ul>
 * {@code mode} and {@code encoding} are matched case-insensitively.
 */
public record SecretIn(
        String mode,
        @Nullable Integer length,
        @Nullable String encoding) {
}

package cbs.nova.starter.helper.model;

import java.util.Map;

/**
 * Input for the built-in {@code jwt} helper.
 *
 * <p>
 * {@code mode} selects the operation and must be {@code "parse"}, {@code "verify"}, {@code "sign"},
 * or {@code "claim"} (case-insensitive):
 * <ul>
 * <li>{@code "parse"}: decode-only inspection of a token. Requires {@code token}. Returns the
 * header and payload as maps plus the raw base64url signature segment. NO signature check.</li>
 * <li>{@code "verify"}: cryptographically verify a token's signature (HS256/HS384/HS512) and
 * {@code exp}/{@code nbf} claim validity. Requires {@code token}, {@code secret}, and
 * {@code algorithm}. Returns the header and payload maps only after verification succeeds.</li>
 * <li>{@code "sign"}: produce a compact JWS using HS256/HS384/HS512. Requires {@code payload} (the
 * claims), {@code secret}, and optionally {@code algorithm} (default {@code HS256}) and
 * {@code ttlSeconds} (default 3600). Returns the compact {@code header.payload.signature}
 * string.</li>
 * <li>{@code "claim"}: decode-only extraction of a single claim from the payload. Requires
 * {@code token} and {@code claimName}. Returns the value of the named claim. NO signature
 * check.</li>
 * </ul>
 *
 * <p>
 * SECURITY: {@code parse} and {@code claim} are decode-only — they do not establish trust. Only
 * {@code verify} checks a signature and time-based claims.
 */
public record JwtIn(String mode, String token, String secret, String algorithm, Long ttlSeconds,
        Map<String, Object> payload, String claimName) {
}

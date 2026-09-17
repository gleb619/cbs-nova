package cbs.nova.starter.helper;

import static cbs.nova.starter.core.StarterConstants.RANDOM_BASE64URL;
import static cbs.nova.starter.core.StarterConstants.RANDOM_MAX_STRING_LENGTH;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.SecretIn;
import cbs.nova.starter.helper.model.SecretOut;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Generates cryptographic random values for security-sensitive use cases.
 *
 * <p>
 * Backed by a single shared {@link SecureRandom} instance, this helper is intended for secrets,
 * tokens, API keys, signing secrets, and idempotency keys — the cases the non-cryptographic
 * {@code random} helper explicitly does not cover.
 *
 * <p>
 * Two modes:
 * <ul>
 * <li>{@code "bytes"} — {@code length} random bytes, returned as {@code "hex"} (default),
 * {@code "base64"}, or {@code "base64url"} encoded string (selected by {@code encoding}).</li>
 * <li>{@code "token"} — URL-safe opaque string of {@code length} characters drawn uniformly from
 * the 64-character Base64url alphabet {@code A-Z a-z 0-9 - _} ({@code RANDOM_BASE64URL}), i.e.
 * exactly 6 bits of entropy per character. A {@code length} of {@code n} yields a token with
 * {@code 6n} bits of entropy; {@code length = 32} (192 bits) is a common choice for session tokens
 * and API keys.</li>
 * </ul>
 *
 * <p>
 * {@code length} must be in {@code [0, 100000]} for both modes; out-of-range values are rejected
 * with an {@link IllegalArgumentException}. {@link SecureRandom} is thread-safe, so the shared
 * instance needs no synchronization.
 */
@Helper(name = "secret")
public class SecretHelper implements Executable<SecretIn, SecretOut> {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  @Override
  public @NonNull Result<SecretOut> execute(@NonNull Context<SecretIn> ctx) {
    try {
      SecretIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "bytes" -> Result.success(new SecretOut(randomBytes(input)));
        case "token" -> Result.success(new SecretOut(randomToken(input)));
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "secret.mode must be one of bytes, token, was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static String randomBytes(SecretIn input) {
    int length = requireLength(input, "bytes");
    byte[] bytes = new byte[length];
    SECURE_RANDOM.nextBytes(bytes);
    String encoding = (input.encoding() == null)
            ? "hex"
            : input.encoding().toLowerCase(Locale.ROOT);
    return switch (encoding) {
      case "hex" -> HexFormat.of().formatHex(bytes);
      case "base64" -> Base64.getEncoder().encodeToString(bytes);
      case "base64url" -> Base64.getUrlEncoder().encodeToString(bytes);
      default -> throw new IllegalArgumentException(
              "secret.bytes.encoding must be one of hex, base64, base64url, was: "
                      + input.encoding());
    };
  }

  private static String randomToken(SecretIn input) {
    int length = requireLength(input, "token");
    if (length == 0) {
      return "";
    }
    // Uniform picks from the 64-char base64url pool: 6 bits of entropy per character.
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(RANDOM_BASE64URL.charAt(SECURE_RANDOM.nextInt(RANDOM_BASE64URL.length())));
    }
    return sb.toString();
  }

  private static int requireLength(SecretIn input, String mode) {
    Integer length = input.length();
    if (length == null) {
      throw new IllegalArgumentException("secret." + mode + " requires length");
    }
    if (length < 0) {
      throw new IllegalArgumentException(
              "secret." + mode + ".length must be >= 0, was: " + length);
    }
    if (length > RANDOM_MAX_STRING_LENGTH) {
      throw new IllegalArgumentException(
              "secret." + mode + ".length must be <= " + RANDOM_MAX_STRING_LENGTH
                      + ", was: " + length);
    }
    return length;
  }
}

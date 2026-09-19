package cbs.nova.starter.vhs.scrub;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic fake-value generator used by the VHS record-time scrubbing.
 *
 * <p>
 * The same {@code original value + field name + seed} triple always yields the same fake, which
 * lets a tape recorded against real data be replayed in a lower environment with stable, correlated
 * values. The derivation is a plain SHA-256 hash — intentionally NOT {@code RandomHelper}, which is
 * documented as non-cryptographic and unsuitable for secret-grade material. The heuristic keeps the
 * fake "same shape" as the original:
 *
 * <ul>
 * <li>fields whose name looks secret ({@code token}, {@code secret}, {@code apiKey}, {@code
 * password}, {@code authorization}, ...) produce a synthetic UUID-like token;</li>
 * <li>a purely numeric string produces another purely numeric string of the same length;</li>
 * <li>any other string produces an alphanumeric string of the same length;</li>
 * <li>numbers and booleans map to a value of the same Java type.</li>
 * </ul>
 */
public final class VhsFaker {

  private static final Pattern ALL_DIGITS = Pattern.compile("\\d+");

  private static final Set<String> SECRET_LIKE_FIELD_NAMES = Set.of(
          "token", "secret", "apikey", "key", "password", "pass", "authorization",
          "accesstoken", "refreshtoken", "clientsecret", "credentials", "privatekey");

  private static final char[] ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
          .toCharArray();

  private static final char[] DIGITS = "0123456789".toCharArray();

  /**
   * Derive a deterministic fake for {@code original}.
   *
   * @param fieldName
   *          the JSON field name (used to shape the fake, e.g. secret-looking names become tokens)
   * @param original
   *          the original value; {@code null} maps to {@code null}
   * @param seed
   *          the scrub seed; the same value + seed always yield the same fake
   */
  public Object fake(String fieldName, @Nullable Object original, @Nullable String seed) {
    if (original == null) {
      return null;
    }
    byte[] digest = sha256(fieldName + '\u0000' + seed + '\u0000' + original);

    if (original instanceof CharSequence) {
      String value = String.valueOf(original);
      if (SECRET_LIKE_FIELD_NAMES.contains(fieldName.toLowerCase())) {
        return uuidFromDigest(digest);
      }
      if (ALL_DIGITS.matcher(value).matches()) {
        return numericString(digest, Math.max(1, value.length()));
      }
      return alphanumericString(digest, Math.max(1, value.length()));
    }
    if (original instanceof Integer) {
      return intFromDigest(digest);
    }
    if (original instanceof Long) {
      return longFromDigest(digest);
    }
    if (original instanceof Double) {
      return Double.longBitsToDouble(longFromDigest(digest));
    }
    if (original instanceof Float) {
      return Float.intBitsToFloat(intFromDigest(digest));
    }
    if (original instanceof Boolean) {
      return (digest[0] & 1) == 1;
    }
    // Unknown scalar shape: leave untouched.
    return original;
  }

  private static byte[] sha256(String input) {
    try {
      return MessageDigest.getInstance("SHA-256")
              .digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String uuidFromDigest(byte[] digest) {
    return new UUID(longFromDigestAt(digest, 0), longFromDigestAt(digest, 8)).toString();
  }

  private static String numericString(byte[] digest, int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(DIGITS[(digest[i] & 0xFF) % DIGITS.length]);
    }
    return sb.toString();
  }

  private static String alphanumericString(byte[] digest, int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(ALPHANUMERIC[(digest[i] & 0xFF) % ALPHANUMERIC.length]);
    }
    return sb.toString();
  }

  private static int intFromDigest(byte[] digest) {
    return (digest[0] & 0xFF) << 24
            | (digest[1] & 0xFF) << 16
            | (digest[2] & 0xFF) << 8
            | (digest[3] & 0xFF);
  }

  private static long longFromDigest(byte[] digest) {
    return longFromDigestAt(digest, 0);
  }

  private static long longFromDigestAt(byte[] digest, int offset) {
    long value = 0L;
    for (int i = 0; i < 8; i++) {
      value = (value << 8) | (digest[offset + i] & 0xFF);
    }
    return value;
  }
}

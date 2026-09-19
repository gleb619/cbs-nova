package cbs.nova.starter.vhs.replay.fake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Generates structurally valid synthetic replacements for replay-time faking.
 *
 * <p>
 * {@link #uuidFor} and {@link #hashFor} use the same SHA-256 derivation as T556's {@code VhsFaker}
 * so that record-time and replay-time faking stay shape-consistent. The domain-specific helpers
 * ({@link #iban}, {@link #email}, {@link #phone}) produce values that survive format validation but
 * never point at real data.
 */
public final class SyntheticValueGenerator {

  private static final char[] ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
          .toCharArray();

  private SyntheticValueGenerator() {
  }

  /**
   * Derive a stable UUID v4 for {@code realValue}. Same input always yields the same UUID within
   * the JVM (the digest is over the bytes, not a session salt), so two callers using this helper
   * independently get the same synthetic value for the same real value.
   */
  @NonNull
  public static String uuidFor(@Nullable String realValue) {
    byte[] digest = sha256(String.valueOf(realValue));
    long high = longFromDigestAt(digest, 0);
    long low = longFromDigestAt(digest, 8);
    // Force RFC 4122 v4 layout: top nibble of high -> 4, top two bits of low -> 10.
    return new UUID((high & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L,
            (low & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L).toString();
  }

  /**
   * Derive a stable alphanumeric token of {@code length} characters. Mirrors
   * {@code VhsFaker#alphanumericString}: same {@code original + seed} yields the same string,
   * independent of the JVM instance.
   */
  @NonNull
  public static String hashFor(@Nullable Object original, @Nullable String seed, int length) {
    int safeLen = Math.max(1, length);
    byte[] digest = sha256(String.valueOf(seed) + '\u0000' + String.valueOf(original));
    StringBuilder sb = new StringBuilder(safeLen);
    for (int i = 0; i < safeLen; i++) {
      sb.append(ALPHANUMERIC[(digest[i] & 0xFF) % ALPHANUMERIC.length]);
    }
    return sb.toString();
  }

  /** A structurally valid fake IBAN with valid MOD-97 check digits, country code {@code FA}. */
  @NonNull
  public static String iban() {
    // FA (fake) is a private-use country code prefix, never assigned by SWIFT.
    return iban("FA", 18);
  }

  /**
   * A structurally valid fake IBAN of {@code bodyLength - 2} body characters, country code
   * {@code country} (two ASCII letters), with correct ISO 13616 check digits.
   */
  @NonNull
  public static String iban(@NonNull String country, int bodyLength) {
    String cc = country.length() != 2 ? "FA" : country.toUpperCase();
    StringBuilder body = new StringBuilder(bodyLength);
    for (int i = 0; i < bodyLength; i++) {
      body.append((char) ('0' + (i % 10)));
    }
    // Check-digit calculation: ISO 13616 rearranges as BBAN + country code + "00", then MOD-97 on
    // numeric form. We use a deterministic body so generated check digits are themselves stable.
    String checkInput = body.toString() + cc + "00";
    int mod = mod97(checkInput);
    int check = 98 - mod;
    return cc + String.format("%02d", check) + body;
  }

  /** A fake email at a non-routable {@code example.test} domain. */
  @NonNull
  public static String email() {
    return email("user");
  }

  /**
   * A fake email {@code <localPart>-<digest>@example.test}; {@code localPart} is lowercased and
   * truncated to 12 characters.
   */
  @NonNull
  public static String email(@NonNull String localPart) {
    String sanitized = localPart.toLowerCase().replaceAll("[^a-z0-9]", "");
    if (sanitized.isEmpty()) {
      sanitized = "user";
    }
    String tail = uuidFor(sanitized).substring(0, 8);
    String lp = sanitized.length() > 12 ? sanitized.substring(0, 12) : sanitized;
    return lp + "-" + tail + "@example.test";
  }

  /** A fake E.164 phone number with country code {@code +1} and ten body digits. */
  @NonNull
  public static String phone() {
    return phone("1", 10);
  }

  /**
   * A fake E.164 phone number: {@code +<countryCode><digits>}. {@code countryCode} is digits-only;
   * {@code bodyLength} is the number of body digits after the country code.
   */
  @NonNull
  public static String phone(@NonNull String countryCode, int bodyLength) {
    String cc = countryCode.replaceAll("[^0-9]", "");
    if (cc.isEmpty()) {
      cc = "1";
    }
    StringBuilder body = new StringBuilder(bodyLength);
    // Use a stable numeric pattern so generated numbers never collide with real ones.
    byte[] digest = sha256("phone:" + cc + ":" + bodyLength);
    for (int i = 0; i < bodyLength; i++) {
      body.append((char) ('0' + ((digest[i] & 0xFF) % 10)));
    }
    return "+" + cc + body;
  }

  private static int mod97(String ibanCandidate) {
    // IBAN MOD-97: rearrange, convert letters to digits (A=10..Z=35), then mod 97 on the big
    // integer. We do it iteratively to avoid big-int math.
    StringBuilder numeric = new StringBuilder(ibanCandidate.length() * 2);
    for (int i = 0; i < ibanCandidate.length(); i++) {
      char c = ibanCandidate.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        numeric.append((int) c - 55);
      } else if (c >= '0' && c <= '9') {
        numeric.append(c);
      }
    }
    int rem = 0;
    for (int i = 0; i < numeric.length(); i++) {
      rem = (rem * 10 + (numeric.charAt(i) - '0')) % 97;
    }
    return rem;
  }

  private static byte[] sha256(String input) {
    try {
      return MessageDigest.getInstance("SHA-256")
              .digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static long longFromDigestAt(byte[] digest, int offset) {
    long value = 0L;
    for (int i = 0; i < 8; i++) {
      value = (value << 8) | (digest[offset + i] & 0xFF);
    }
    return value;
  }
}

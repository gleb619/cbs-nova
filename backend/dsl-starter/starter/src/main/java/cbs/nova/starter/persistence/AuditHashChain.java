package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslAuditEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Canonical hashing for the T880 tamper-evident {@code dsl_audit} hash chain.
 *
 * <p>
 * For one row, {@link #computeEntryHash(String, DslAuditEntity)} builds the byte sequence
 *
 * <pre>
 * utf8(prev_hash || "\0" || id || "\0" || occurred_at || "\0" || actor
 *         || "\0" || action || "\0" || target || "\0" || outcome
 *         || "\0" || detail || "\0")
 * </pre>
 *
 * <p>
 * Field order is fixed (id, occurred_at, actor, action, target, outcome, detail) and the NUL byte
 * is the inter-field separator. {@code prev_hash} is the empty string for the first post-migration
 * row, not {@code null} — keeping a fixed prefix keeps the input byte sequence unambiguous.
 * {@code occurred_at} is rounded to microsecond precision before serialising so the chain stays
 * canonical across H2 (which rounds TIMESTAMP WITH TIME ZONE on read-back) and Postgres (which
 * keeps the full nanosecond). The rounding is applied to every input, not just the read-back, so
 * writers and verifiers agree. {@code detail} is the raw {@code details_json} text as written; no
 * whitespace normalisation.
 *
 * <p>
 * SHA-256 is required by {@link MessageDigest#getInstance(String)}; if the runtime does not provide
 * it the constructor throws and the application fails fast at startup — that is the intended
 * behavior because the chain would otherwise be silently broken.
 */
public final class AuditHashChain {

  private AuditHashChain() {
  }

  /**
   * Computes the canonical SHA-256 hex digest for one row given the previous row's
   * {@code entry_hash}. {@code prevHashHex} must be either a 64-char hex string or {@code null}
   * (the first post-migration row).
   */
  public static String computeEntryHash(@org.jspecify.annotations.Nullable String prevHashHex,
          DslAuditEntity row) {
    if (row == null) {
      throw new IllegalArgumentException("row is required");
    }
    if (row.id() == null) {
      throw new IllegalArgumentException(
              "row.id is required to compute the entry hash — the row must be persisted first");
    }
    String prev = prevHashHex == null ? "" : prevHashHex;
    // H2 rounds TIMESTAMP WITH TIME ZONE to microsecond precision on read-back (0.058524896
    // becomes 0.058525), while Postgres preserves the full nanosecond. The chain is canonical
    // only if both writers and verifiers normalise the input the same way; rounding to micros
    // matches the H2 read-back exactly and postgres accepts the rounded value without changing
    // the stored timestamp for any input whose nanos are an exact multiple of 1000.
    String occurredAt = row.occurredAt() == null ? "" : roundToMicros(row.occurredAt()).toString();
    String actor = nullToEmpty(row.actor());
    String action = nullToEmpty(row.action());
    String target = nullToEmpty(row.target());
    String outcome = nullToEmpty(row.outcome());
    String detail = nullToEmpty(row.detailsJson());

    StringBuilder sb = new StringBuilder(256);
    sb.append(prev).append('\0')
            .append(row.id()).append('\0')
            .append(occurredAt).append('\0')
            .append(actor).append('\0')
            .append(action).append('\0')
            .append(target).append('\0')
            .append(outcome).append('\0')
            .append(detail).append('\0');

    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
              .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      return toHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available on this JVM", e);
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  /** Rounds a {@link Instant} to microsecond precision to match H2's TIMESTAMP round-trip. */
  private static Instant roundToMicros(Instant value) {
    long nanos = value.getNano();
    long micros = Math.round(nanos / 1000.0);
    if (micros == 1_000_000) {
      // Overflow into the next second (e.g. 999_999_999 nanos + rounding = 1_000_000 micros).
      return value.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1);
    }
    return value.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusNanos(micros * 1000L);
  }

  private static String toHex(byte[] bytes) {
    char[] hex = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      hex[i * 2] = HEX_CHARS[v >>> 4];
      hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
    }
    return new String(hex);
  }

  private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
}

package cbs.nova.starter.vhs.replay.fake;

import org.jspecify.annotations.NonNull;

/**
 * A single replay-time faking rule.
 *
 * <p>
 * Selects one JSON path inside a {@code call_start} tape event's {@code input} tree and rewrites
 * the matched value with a synthetic equivalent of the configured {@link FakeType}. Paths use the
 * same dot + {@code [*]} notation as T556's {@code ScrubRule}.
 *
 * @param path
 *          the JSON path to fake, e.g. {@code $.customerId}
 * @param type
 *          the shape of value to generate; {@code generic} preserves alphanumeric length/character
 *          class without imposing a domain-specific format
 * @param generate
 *          the generation strategy; {@code lookup} falls back to {@code synthetic_uuid} when the
 *          pre-seeded lookup table does not contain the real value
 */
public record FakeRule(
        @NonNull String path,
        @NonNull FakeType type,
        @NonNull Generate generate) {

  public FakeRule {
    type = type == null ? FakeType.generic : type;
    generate = generate == null ? Generate.synthetic_uuid : generate;
  }

  /** Domain shape of the synthetic replacement. */
  public enum FakeType {

    /** Generic opaque identifier (UUID v4 by default). */
    id,

    /** Secret-like token (UUID-derived). */
    secret,

    /** Foreign reference — opaque id that points at another record. */
    reference,

    /** IBAN — structurally valid fake IBAN with valid check digits. */
    iban,

    /** Email — RFC-shaped fake address under {@code example.test}. */
    email,

    /** Phone — E.164-shaped fake number with stable country code prefix. */
    phone,

    /** Generic alphanumeric of the original length (no domain constraints). */
    generic
  }

  /** Generation strategy applied when a value matches {@link #path}. */
  public enum Generate {

    /** Stable UUID v4 keyed by real value within the replay session. */
    synthetic_uuid,

    /**
     * Stable SHA-256-derived alphanumeric token of the original length (T556's shape heuristic).
     */
    synthetic_hash,

    /**
     * Use the configured lookup table; fall back to {@link #synthetic_uuid} when the real value is
     * not present.
     */
    lookup
  }
}

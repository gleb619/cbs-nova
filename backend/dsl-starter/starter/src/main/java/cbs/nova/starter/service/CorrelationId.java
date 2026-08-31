package cbs.nova.starter.service;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Validates caller-supplied {@code X-Correlation-Id} header values.
 *
 * <p>
 * The correlation id is entirely caller-supplied: the server never generates one. It is persisted
 * on the dsl_runs row and exposed on the executions list + detail endpoints so operators can trace
 * a business transaction end-to-end.
 */
public final class CorrelationId {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String CORRELATION_ID_METADATA_KEY = "correlationId";

  private static final int MAX_LENGTH = 200;
  private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:/-]+$");

  private CorrelationId() {
  }

  /**
   * Validates a raw header value.
   *
   * @param value
   *          the raw header value; {@code null} means the header was absent
   * @return the trimmed, valid correlation id, or {@code null} when the header is absent
   * @throws IllegalArgumentException
   *           when the header is present but violates the length or charset rules
   */
  public static @Nullable String validated(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Invalid X-Correlation-Id header");
    }
    if (!VALID_PATTERN.matcher(trimmed).matches()) {
      throw new IllegalArgumentException("Invalid X-Correlation-Id header");
    }
    return trimmed;
  }

  /**
   * Extracts a correlation id from DSL execution metadata.
   *
   * @param value
   *          the metadata value, if any
   * @return the trimmed correlation id when it is a non-blank string, otherwise {@code null}
   */
  public static @Nullable String fromMetadata(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      String trimmed = s.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }
    return null;
  }
}

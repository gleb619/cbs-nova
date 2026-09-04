package cbs.nova.starter.helper.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code metric} helper.
 *
 * <p>
 * One of four {@code type} values selects the meter that gets registered/updated against the
 * optional Micrometer {@code MeterRegistry} bean:
 *
 * <ul>
 * <li>{@code "counter"} — requires {@code amount} (default {@code 1}).</li>
 * <li>{@code "gauge"} — requires {@code value}; last call with the same {@code name}+{@code tags}
 * wins.</li>
 * <li>{@code "timer"} — requires {@code durationMs} (non-negative).</li>
 * <li>{@code "summary"} — requires {@code value} (recorded into a
 * {@code DistributionSummary}).</li>
 * </ul>
 *
 * <p>
 * {@code tags} defaults to an empty map. Null map keys are rejected; null values are coerced to the
 * empty string (Micrometer's {@code Tag.of} forbids null values).
 */
public record MetricIn(
        @Nullable String type,
        @Nullable String name,
        @Nullable Map<String, String> tags,
        @Nullable Double value,
        @Nullable Long amount,
        @Nullable Long durationMs) {

  public Map<String, String> effectiveTags() {
    return tags == null ? Map.of() : tags;
  }
}

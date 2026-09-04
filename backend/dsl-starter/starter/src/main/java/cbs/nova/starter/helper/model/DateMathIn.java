package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code dateMath} helper.
 *
 * <p>
 * The {@code op} discriminator selects the operation:
 * <ul>
 * <li>{@code "add"} — add an {@code amount} in {@code unit} to {@code date}; populates
 * {@code value}.</li>
 * <li>{@code "diff"} — signed difference between {@code date} and {@code end} in {@code unit};
 * populates {@code number}.</li>
 * <li>{@code "before"} / {@code "after"} — comparison; populates {@code flag}.</li>
 * <li>{@code "startOf"} — truncate {@code date} to the start of {@code unit}; populates
 * {@code value}.</li>
 * </ul>
 *
 * <p>
 * {@code unit} accepts {@code {millis, seconds, minutes, hours, days, weeks, months, years}} for
 * {@code add}/{@code diff} and {@code {minute, hour, day, month, year}} for {@code startOf}.
 * {@code zone} defaults to {@code "UTC"} when null/blank and must be a valid IANA zone id (see
 * {@link java.time.ZoneId}).
 */
public record DateMathIn(String op, String date, String end, Long amount, String unit,
        String zone) {

  /** Returns {@code zone} or {@code "UTC"} when null/blank. */
  public String effectiveZone() {
    return (zone == null || zone.isBlank()) ? "UTC" : zone;
  }

  /** Returns {@code unit} or {@code def} when null/blank. */
  public String effectiveUnit(String def) {
    return (unit == null || unit.isBlank()) ? def : unit;
  }
}

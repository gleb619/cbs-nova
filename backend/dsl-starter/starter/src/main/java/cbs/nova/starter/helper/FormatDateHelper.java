package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.FormatDateIn;
import cbs.nova.starter.helper.model.FormatDateOut;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Formats an instant into a human-readable date/time string using a preset or custom pattern and an
 * optional time zone.
 *
 * <p>
 * The source instant can be supplied as:
 * <ul>
 * <li>a numeric epoch-millis string ({@code -?\d+})&lt;/li>
 * <li>an ISO-8601 instant such as {@code 2026-03-15T12:00:00Z}&lt;/li>
 * <li>an ISO-8601 offset or zoned date/time&lt;/li>
 * <li>a local date, which is interpreted as the start of that day in the target zone&lt;/li>
 * </ul>
 *
 * <p>
 * Supported preset pattern aliases (case-sensitive) are {@code ISO_INSTANT},
 * {@code ISO_LOCAL_DATE}, {@code ISO_OFFSET_DATE_TIME}, {@code ISO_ZONED_DATE_TIME} and
 * {@code RFC_1123_DATE_TIME}. Any other value is treated as a raw
 * {@link java.time.format.DateTimeFormatter#ofPattern(String, Locale)} string.
 */
@Helper(name = "formatDate")
public class FormatDateHelper implements Executable<FormatDateIn, FormatDateOut> {

  private static final Pattern EPOCH_MILLIS_PATTERN = Pattern.compile("-?\\d+");

  @Override
  public @NonNull Result<FormatDateOut> execute(@NonNull Context<FormatDateIn> ctx) {
    try {
      FormatDateIn input = ctx.body();
      if (input.input() == null || input.input().isBlank()) {
        return Result.failure(new IllegalArgumentException("formatDate.input is required"));
      }
      ZoneId zone = resolveZone(input.zone());
      Instant instant = parseInstant(input.input(), zone);
      DateTimeFormatter formatter = buildFormatter(input.pattern());
      String formatted = formatter.withZone(zone).format(instant.atZone(zone));
      return Result.success(new FormatDateOut(formatted));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static Instant parseInstant(String value, ZoneId zone) {
    String trimmed = value.trim();
    if (EPOCH_MILLIS_PATTERN.matcher(trimmed).matches()) {
      return Instant.ofEpochMilli(Long.parseLong(trimmed));
    }
    try {
      return Instant.parse(trimmed);
    } catch (DateTimeParseException ignored) {
      // continue with ladder
    }
    try {
      return OffsetDateTime.parse(trimmed).toInstant();
    } catch (DateTimeParseException ignored) {
      // continue with ladder
    }
    try {
      return ZonedDateTime.parse(trimmed).toInstant();
    } catch (DateTimeParseException ignored) {
      // continue with ladder
    }
    try {
      return LocalDate.parse(trimmed).atStartOfDay(zone).toInstant();
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
              "formatDate.input is not a recognized date/time: " + value, e);
    }
  }

  private static ZoneId resolveZone(String zone) {
    try {
      return (zone != null && !zone.isBlank()) ? ZoneId.of(zone) : ZoneId.of("UTC");
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("formatDate.zone is not a valid zone id: " + zone, e);
    }
  }

  private static DateTimeFormatter buildFormatter(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("formatDate.pattern is required");
    }
    String trimmed = pattern.trim();
    DateTimeFormatter preset = resolvePreset(trimmed);
    if (preset != null) {
      return preset;
    }
    try {
      return DateTimeFormatter.ofPattern(trimmed, Locale.ROOT);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("formatDate.pattern is invalid: " + pattern, e);
    }
  }

  private static DateTimeFormatter resolvePreset(String pattern) {
    return switch (pattern) {
      case "ISO_INSTANT" -> DateTimeFormatter.ISO_INSTANT;
      case "ISO_LOCAL_DATE" -> DateTimeFormatter.ISO_LOCAL_DATE;
      case "ISO_OFFSET_DATE_TIME" -> DateTimeFormatter.ISO_OFFSET_DATE_TIME;
      case "ISO_ZONED_DATE_TIME" -> DateTimeFormatter.ISO_ZONED_DATE_TIME;
      case "RFC_1123_DATE_TIME" -> DateTimeFormatter.RFC_1123_DATE_TIME;
      default -> null;
    };
  }
}

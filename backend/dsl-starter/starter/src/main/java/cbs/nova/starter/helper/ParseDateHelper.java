package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.ParseDateIn;
import cbs.nova.starter.helper.model.ParseDateOut;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Parses a date/time string into a normalized ISO-8601 instant using a preset or custom pattern and
 * an optional time zone.
 *
 * <p>
 * Supported preset pattern aliases (case-sensitive) are {@code ISO_INSTANT},
 * {@code ISO_LOCAL_DATE}, {@code ISO_OFFSET_DATE_TIME}, {@code ISO_ZONED_DATE_TIME} and
 * {@code RFC_1123_DATE_TIME}. Any other value is treated as a raw
 * {@link java.time.format.DateTimeFormatter#ofPattern(String, Locale)} string.
 *
 * <p>
 * The result is always an ISO-8601 instant string ending in {@code Z}, making {@code parseDate} the
 * symmetric counterpart of {@link FormatDateHelper}.
 */
@Helper(name = "parseDate")
public class ParseDateHelper implements Executable<ParseDateIn, ParseDateOut> {

  @Override
  public @NonNull Result<ParseDateOut> execute(@NonNull Context<ParseDateIn> ctx) {
    ParseDateIn input = ctx.body();
    try {
      if (input.input() == null || input.input().isBlank()) {
        return Result.failure(new IllegalArgumentException("parseDate.input is required"));
      }
      ZoneId zone = resolveZone(input.zone());
      DateTimeFormatter formatter = buildFormatter(input.pattern());
      TemporalAccessor parsed = formatter.withZone(zone).parseBest(
              input.input().trim(),
              ZonedDateTime::from,
              LocalDateTime::from,
              LocalDate::from,
              Instant::from);
      Instant instant = toInstant(parsed, zone);
      String iso = DateTimeFormatter.ISO_INSTANT.format(instant);
      return Result.success(new ParseDateOut(iso));
    } catch (DateTimeParseException e) {
      return Result.failure(new IllegalArgumentException(
              "parseDate.input does not match pattern: " + input.input(), e));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static Instant toInstant(TemporalAccessor parsed, ZoneId zone) {
    if (parsed instanceof ZonedDateTime zdt) {
      return zdt.toInstant();
    }
    if (parsed instanceof LocalDateTime ldt) {
      return ldt.atZone(zone).toInstant();
    }
    if (parsed instanceof LocalDate ld) {
      return ld.atStartOfDay(zone).toInstant();
    }
    if (parsed instanceof Instant instant) {
      return instant;
    }
    throw new IllegalArgumentException(
            "parseDate.input could not be resolved to an instant: " + parsed);
  }

  private static ZoneId resolveZone(String zone) {
    try {
      return (zone != null && !zone.isBlank()) ? ZoneId.of(zone) : ZoneId.of("UTC");
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("parseDate.zone is not a valid zone id: " + zone, e);
    }
  }

  private static DateTimeFormatter buildFormatter(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("parseDate.pattern is required");
    }
    String trimmed = pattern.trim();
    DateTimeFormatter preset = resolvePreset(trimmed);
    if (preset != null) {
      return preset;
    }
    try {
      return DateTimeFormatter.ofPattern(trimmed, Locale.ROOT);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("parseDate.pattern is invalid: " + pattern, e);
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

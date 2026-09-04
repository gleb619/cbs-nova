package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.DateMathIn;
import cbs.nova.starter.helper.model.DateMathOut;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * Date/time arithmetic helper.
 *
 * <p>
 * Operations are selected by the {@code op} discriminator:
 * <ul>
 * <li>{@code "add"} — add {@code amount} in {@code unit} to {@code date}; returns a string in
 * {@code value}.</li>
 * <li>{@code "diff"} — signed difference between {@code date} and {@code end} in {@code unit};
 * returns a long in {@code number} (negative when {@code date > end}).</li>
 * <li>{@code "before"} / {@code "after"} — comparison; returns a boolean in {@code flag}.</li>
 * <li>{@code "startOf"} — truncate {@code date} to the start of {@code unit}; returns a string in
 * {@code value}.</li>
 * </ul>
 *
 * <p>
 * Output precision mirrors input precision. A bare {@code LocalDate} input ({@code yyyy-MM-dd})
 * produces a bare {@code LocalDate} string; anything else (instant / offset / zoned) is normalized
 * to an ISO-8601 instant string ending in {@code Z}.
 *
 * <p>
 * Months and years use calendar arithmetic ({@link java.time.LocalDate#plusMonths},
 * {@link java.time.ZonedDateTime#plusMonths}) — Jan 31 + 1 month → Feb 28, Feb 29 + 1 year → Feb
 * 28. Days and weeks use
 * {@link java.time.ZonedDateTime#plus(long, java.time.temporal.TemporalUnit)} which preserves
 * wall-clock across DST transitions (so
 * {@code add("2026-03-08T06:00:00Z", 1, "days", "America/New_York")} lands on
 * {@code 2026-03-09T05:00:00Z} — 23 h later, not 24 h).
 */
@Helper(name = "dateMath")
public class DateMathHelper implements Executable<DateMathIn, DateMathOut> {

  private static final Set<String> ADD_UNITS = Set.of("millis", "seconds", "minutes", "hours",
          "days", "weeks", "months", "years");

  private static final Set<String> DIFF_UNITS = ADD_UNITS;

  private static final Set<String> START_OF_UNITS = Set.of("minute", "hour", "day", "month",
          "year");

  private static final Set<String> DATE_ONLY_ADD_UNITS = Set.of("days", "weeks", "months", "years");

  private static final Set<String> DATE_ONLY_START_OF_UNITS = Set.of("day", "month", "year");

  @Override
  public @NonNull Result<DateMathOut> execute(@NonNull Context<DateMathIn> ctx) {
    try {
      DateMathIn input = ctx.body();
      if (input.date() == null || input.date().isBlank()) {
        return Result.failure(new IllegalArgumentException("dateMath.date is required"));
      }
      String op = (input.op() == null) ? null : input.op().toLowerCase(Locale.ROOT);
      ZoneId zone = resolveZone(input.effectiveZone());
      return switch (op) {
        case "add" -> opAdd(input, zone);
        case "diff" -> opDiff(input, zone);
        case "before" -> opBefore(input, zone);
        case "after" -> opAfter(input, zone);
        case "startof" -> opStartOf(input, zone);
        case null, default -> Result.failure(new IllegalArgumentException(
                "dateMath.op must be one of: add, diff, before, after, startOf, was: "
                        + input.op()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  // ---- ops -----------------------------------------------------------------

  private static Result<DateMathOut> opAdd(DateMathIn input, ZoneId zone) {
    if (input.amount() == null) {
      return Result.failure(new IllegalArgumentException("dateMath.amount is required"));
    }
    String unit = input.effectiveUnit("days").toLowerCase(Locale.ROOT);
    if (!ADD_UNITS.contains(unit)) {
      return Result.failure(
              new IllegalArgumentException(
                      "dateMath.unit must be one of: " + ADD_UNITS + ", was: " + input.unit()));
    }
    ParsedDate parsed = parseDate(input.date(), zone);
    if (parsed.isDateOnly() && !DATE_ONLY_ADD_UNITS.contains(unit)) {
      return Result.failure(
              new IllegalArgumentException(
                      "dateMath.unit " + unit + " is not valid for a date-only value"));
    }
    String result = renderValue(addValue(parsed, input.amount(), unit, zone), zone);
    return Result.success(new DateMathOut(result, null, null));
  }

  private static Result<DateMathOut> opDiff(DateMathIn input, ZoneId zone) {
    if (input.end() == null || input.end().isBlank()) {
      return Result.failure(new IllegalArgumentException("dateMath.end is required"));
    }
    String unit = input.effectiveUnit("millis").toLowerCase(Locale.ROOT);
    if (!DIFF_UNITS.contains(unit)) {
      return Result.failure(
              new IllegalArgumentException(
                      "dateMath.unit must be one of: " + DIFF_UNITS + ", was: " + input.unit()));
    }
    ParsedDate start = parseDate(input.date(), zone);
    ParsedDate end = parseDate(input.end(), zone);
    long number = diffValue(start, end, unit);
    return Result.success(new DateMathOut(null, number, null));
  }

  private static Result<DateMathOut> opBefore(DateMathIn input, ZoneId zone) {
    ParsedDate a = parseDate(input.date(), zone);
    ParsedDate b = parseDate(input.end(), zone);
    return Result.success(new DateMathOut(null, null, a.isBefore(b)));
  }

  private static Result<DateMathOut> opAfter(DateMathIn input, ZoneId zone) {
    ParsedDate a = parseDate(input.date(), zone);
    ParsedDate b = parseDate(input.end(), zone);
    return Result.success(new DateMathOut(null, null, a.isAfter(b)));
  }

  private static Result<DateMathOut> opStartOf(DateMathIn input, ZoneId zone) {
    String unit = input.effectiveUnit("day").toLowerCase(Locale.ROOT);
    if (!START_OF_UNITS.contains(unit)) {
      return Result.failure(
              new IllegalArgumentException(
                      "dateMath.unit must be one of: " + START_OF_UNITS + ", was: "
                              + input.unit()));
    }
    ParsedDate parsed = parseDate(input.date(), zone);
    if (parsed.isDateOnly() && !DATE_ONLY_START_OF_UNITS.contains(unit)) {
      return Result.failure(
              new IllegalArgumentException(
                      "dateMath.unit " + unit + " is not valid for a date-only value"));
    }
    String result = renderValue(startOfValue(parsed, unit, zone), zone);
    return Result.success(new DateMathOut(result, null, null));
  }

  // ---- pure helpers --------------------------------------------------------

  private static ParsedDate addValue(ParsedDate parsed, long amount, String unit, ZoneId zone) {
    if (parsed.isDateOnly()) {
      LocalDate ld = parsed.localDate();
      LocalDate result = switch (unit) {
        case "days" -> ld.plusDays(amount);
        case "weeks" -> ld.plusDays(Math.multiplyExact(amount, 7L));
        case "months" -> ld.plusMonths(amount);
        case "years" -> ld.plusYears(amount);
        default -> throw new IllegalStateException("unreachable");
      };
      return ParsedDate.ofDate(result);
    }
    ZonedDateTime zdt = parsed.zonedDateTime().withZoneSameInstant(zone);
    ZonedDateTime result = switch (unit) {
      case "millis" -> zdt.plus(amount, ChronoUnit.MILLIS);
      case "seconds" -> zdt.plus(amount, ChronoUnit.SECONDS);
      case "minutes" -> zdt.plus(amount, ChronoUnit.MINUTES);
      case "hours" -> zdt.plus(amount, ChronoUnit.HOURS);
      case "days" -> zdt.plus(amount, ChronoUnit.DAYS);
      case "weeks" -> zdt.plus(amount, ChronoUnit.WEEKS);
      case "months" -> zdt.plusMonths(amount);
      case "years" -> zdt.plusYears(amount);
      default -> throw new IllegalStateException("unreachable");
    };
    return ParsedDate.ofZoned(result);
  }

  private static long diffValue(ParsedDate start, ParsedDate end, String unit) {
    Instant s = start.toInstant();
    Instant e = end.toInstant();
    return switch (unit) {
      case "millis" -> Duration.between(s, e).toMillis();
      case "seconds" -> Duration.between(s, e).toSeconds();
      case "minutes" -> Duration.between(s, e).toMinutes();
      case "hours" -> Duration.between(s, e).toHours();
      case "days" -> ChronoUnit.DAYS.between(s, e);
      case "weeks" -> ChronoUnit.DAYS.between(s, e) / 7L;
      case "months" -> ChronoUnit.MONTHS.between(s, e);
      case "years" -> ChronoUnit.YEARS.between(s, e);
      default -> throw new IllegalStateException("unreachable");
    };
  }

  private static ParsedDate startOfValue(ParsedDate parsed, String unit, ZoneId zone) {
    if (parsed.isDateOnly()) {
      LocalDate ld = parsed.localDate();
      LocalDate result = switch (unit) {
        case "day" -> ld;
        case "month" -> ld.withDayOfMonth(1);
        case "year" -> ld.withDayOfYear(1);
        default -> throw new IllegalStateException("unreachable");
      };
      return ParsedDate.ofDate(result);
    }
    ZonedDateTime zdt = parsed.zonedDateTime().withZoneSameInstant(zone);
    ZonedDateTime result = switch (unit) {
      case "minute" -> zdt.truncatedTo(ChronoUnit.MINUTES);
      case "hour" -> zdt.truncatedTo(ChronoUnit.HOURS);
      case "day" -> zdt.toLocalDate().atStartOfDay(zone);
      case "month" -> zdt.toLocalDate().withDayOfMonth(1).atStartOfDay(zone);
      case "year" -> zdt.toLocalDate().withDayOfYear(1).atStartOfDay(zone);
      default -> throw new IllegalStateException("unreachable");
    };
    return ParsedDate.ofZoned(result);
  }

  private static String renderValue(ParsedDate parsed, ZoneId zone) {
    if (parsed.isDateOnly()) {
      return parsed.localDate().toString();
    }
    return DateTimeFormatter.ISO_INSTANT
            .format(parsed.zonedDateTime().withZoneSameInstant(zone).toInstant());
  }

  // ---- parsing + zone ------------------------------------------------------

  private static ZoneId resolveZone(String zone) {
    try {
      return ZoneId.of(zone);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("dateMath.zone is not a valid zone id: " + zone, e);
    }
  }

  private static ParsedDate parseDate(String raw, ZoneId zone) {
    String trimmed = raw.trim();
    try {
      return ParsedDate.ofDate(LocalDate.parse(trimmed));
    } catch (DateTimeParseException ignored) {
      // not a bare LocalDate — fall through to the instant/offset/zoned ladder
    }
    try {
      return ParsedDate.ofZoned(ZonedDateTime.ofInstant(Instant.parse(trimmed), zone));
    } catch (DateTimeParseException ignored) {
      // continue
    }
    try {
      return ParsedDate.ofZoned(OffsetDateTime.parse(trimmed).atZoneSameInstant(zone));
    } catch (DateTimeParseException ignored) {
      // continue
    }
    try {
      return ParsedDate.ofZoned(ZonedDateTime.parse(trimmed).withZoneSameInstant(zone));
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
              "dateMath.date is not a recognized date/time: " + raw, e);
    }
  }

  // ---- internal parsed-date holder ----------------------------------------

  private record ParsedDate(LocalDate localDate, ZonedDateTime zonedDateTime) {
    static ParsedDate ofDate(LocalDate ld) {
      return new ParsedDate(ld, null);
    }

    static ParsedDate ofZoned(ZonedDateTime zdt) {
      return new ParsedDate(null, zdt);
    }

    boolean isDateOnly() {
      return localDate != null;
    }

    Instant toInstant() {
      return isDateOnly()
              ? localDate.atStartOfDay(ZoneId.of("UTC")).toInstant()
              : zonedDateTime.toInstant();
    }

    boolean isBefore(ParsedDate other) {
      return toInstant().isBefore(other.toInstant());
    }

    boolean isAfter(ParsedDate other) {
      return toInstant().isAfter(other.toInstant());
    }
  }
}

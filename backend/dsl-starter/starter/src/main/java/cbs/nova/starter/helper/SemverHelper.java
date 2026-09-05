package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.SemverIn;
import cbs.nova.starter.helper.model.SemverOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Parses, compares, range-checks, bumps and formats versions that conform to the Semantic
 * Versioning 2.0.0 specification (https://semver.org).
 *
 * <p>
 * The helper supports five modes (case-insensitive):
 * <ul>
 * <li>{@code "parse"}: returns the {@code major}/{@code minor}/{@code patch} integers and the
 * optional {@code preRelease} and {@code build} identifiers from {@code version}.</li>
 * <li>{@code "compare"}: returns {@code -1}, {@code 0} or {@code 1} per spec §11 precedence rules
 * (build metadata ignored).</li>
 * <li>{@code "satisfies"}: returns whether {@code version} matches {@code range}. Supported range
 * syntax is exact ({@code "1.2.3"}), caret ({@code "^1.2.3"}), tilde ({@code "~1.2.3"}), a
 * comparator ({@code ">="}, {@code ">"}, {@code "<="}, {@code "<"}), or a partial wildcard
 * ({@code "1.x"}, {@code "1.2.x"}).</li>
 * <li>{@code "bump"}: returns a new version after incrementing {@code major}, {@code minor},
 * {@code patch} or the last numeric prerelease identifier.</li>
 * <li>{@code "format"}: composes a version string from {@code major}/{@code minor}/{@code patch}
 * and the optional {@code preRelease} and {@code build} identifiers.</li>
 * </ul>
 *
 * <p>
 * A leading {@code "v"} on the input version is stripped before parsing. Unknown {@code mode} or
 * {@code bumpType}, malformed versions and malformed ranges all yield an
 * {@link IllegalArgumentException}.
 */
@Helper(name = "semver")
public class SemverHelper implements Executable<SemverIn, SemverOut> {

  // Official semver.org-recommended regex
  // (https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string).
  private static final String SEMVER_REGEX = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
          + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)"
          + "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
          + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$";
  private static final Pattern SEMVER_PATTERN = Pattern.compile(SEMVER_REGEX);

  private static final String IDENT_REGEX = "[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*";

  @Override
  public @NonNull Result<SemverOut> execute(@NonNull Context<SemverIn> ctx) {
    try {
      SemverIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "parse" -> parseMode(input.version());
        case "compare" -> compareMode(input.versionA(), input.versionB());
        case "satisfies" -> satisfiesMode(input.version(), input.range());
        case "bump" -> bumpMode(input.version(), input.bumpType());
        case "format" -> formatMode(input.major(), input.minor(), input.patch(),
                input.preRelease(), input.build());
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "semver.mode must be one of parse, compare, satisfies, bump, format,"
                                + " was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  // --- mode: parse -----------------------------------------------------

  private static @NonNull Result<SemverOut> parseMode(String version) {
    Parsed parsed = requireParsed(version);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("major", parsed.major);
    result.put("minor", parsed.minor);
    result.put("patch", parsed.patch);
    result.put("preRelease", parsed.preRelease);
    result.put("build", parsed.build);
    return Result.success(new SemverOut(result));
  }

  // --- mode: compare ---------------------------------------------------

  private static @NonNull Result<SemverOut> compareMode(String versionA, String versionB) {
    Parsed a = requireParsed(versionA);
    Parsed b = requireParsed(versionB);
    int cmp = Integer.compare(a.major, b.major);
    if (cmp == 0) {
      cmp = Integer.compare(a.minor, b.minor);
    }
    if (cmp == 0) {
      cmp = Integer.compare(a.patch, b.patch);
    }
    if (cmp == 0) {
      cmp = comparePreRelease(a.preRelease, b.preRelease);
    }
    return Result.success(new SemverOut(Integer.signum(cmp)));
  }

  private static int comparePreRelease(String a, String b) {
    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return 1;
    }
    if (b == null) {
      return -1;
    }
    List<String> aIds = List.of(a.split("\\."));
    List<String> bIds = List.of(b.split("\\."));
    int limit = Math.min(aIds.size(), bIds.size());
    for (int i = 0; i < limit; i++) {
      int cmp = compareIdentifier(aIds.get(i), bIds.get(i));
      if (cmp != 0) {
        return cmp;
      }
    }
    if (aIds.size() == bIds.size()) {
      return 0;
    }
    return aIds.size() < bIds.size() ? -1 : 1;
  }

  private static int compareIdentifier(String a, String b) {
    boolean aNumeric = isNumericIdentifier(a);
    boolean bNumeric = isNumericIdentifier(b);
    if (aNumeric && bNumeric) {
      return Long.compare(Long.parseLong(a), Long.parseLong(b));
    }
    if (aNumeric) {
      return -1;
    }
    if (bNumeric) {
      return 1;
    }
    return a.compareTo(b);
  }

  private static boolean isNumericIdentifier(String id) {
    if (id.isEmpty()) {
      return false;
    }
    for (int i = 0; i < id.length(); i++) {
      if (!Character.isDigit(id.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  // --- mode: satisfies -------------------------------------------------

  private static @NonNull Result<SemverOut> satisfiesMode(String version, String range) {
    if (range == null || range.isBlank()) {
      throw new IllegalArgumentException("semver.satisfies: range is required");
    }
    Parsed target = requireParsed(version);
    String trimmed = range.trim();
    boolean result;
    if (trimmed.startsWith("^")) {
      result = satisfiesCaret(target, trimmed.substring(1).trim());
    } else if (trimmed.startsWith("~")) {
      result = satisfiesTilde(target, trimmed.substring(1).trim());
    } else if (trimmed.startsWith(">=")) {
      result = compareParsed(target, requireParsed(trimmed.substring(2).trim())) >= 0;
    } else if (trimmed.startsWith(">")) {
      result = compareParsed(target, requireParsed(trimmed.substring(1).trim())) > 0;
    } else if (trimmed.startsWith("<=")) {
      result = compareParsed(target, requireParsed(trimmed.substring(2).trim())) <= 0;
    } else if (trimmed.startsWith("<")) {
      result = compareParsed(target, requireParsed(trimmed.substring(1).trim())) < 0;
    } else if (trimmed.endsWith(".x") || trimmed.endsWith(".X")
            || trimmed.endsWith(".*")) {
      result = satisfiesWildcard(target, trimmed);
    } else {
      result = compareParsed(target, requireParsed(trimmed)) == 0;
    }
    return Result.success(new SemverOut(result));
  }

  private static boolean satisfiesCaret(Parsed target, String rangeBody) {
    Parsed range = requireParsed(rangeBody);
    if (compareParsed(target, range) < 0) {
      return false;
    }
    int upperMajor;
    int upperMinor;
    int upperPatch;
    if (range.major > 0) {
      upperMajor = range.major + 1;
      upperMinor = 0;
      upperPatch = 0;
    } else if (range.minor > 0) {
      upperMajor = 0;
      upperMinor = range.minor + 1;
      upperPatch = 0;
    } else {
      upperMajor = 0;
      upperMinor = 0;
      upperPatch = range.patch + 1;
    }
    Parsed upper = new Parsed(upperMajor, upperMinor, upperPatch, null, null);
    return compareParsed(target, upper) < 0;
  }

  private static boolean satisfiesTilde(Parsed target, String rangeBody) {
    Parsed range = requireParsed(rangeBody);
    if (compareParsed(target, range) < 0) {
      return false;
    }
    Parsed upper = new Parsed(range.major, range.minor + 1, 0, null, null);
    return compareParsed(target, upper) < 0;
  }

  private static boolean satisfiesWildcard(Parsed target, String range) {
    String body = range.substring(0, range.length() - 2);
    int firstDot = body.indexOf('.');
    int secondDot = body.indexOf('.', firstDot + 1);
    String majorText;
    String minorText;
    if (firstDot < 0) {
      majorText = body;
      minorText = "0";
    } else {
      majorText = body.substring(0, firstDot);
      if (secondDot < 0) {
        minorText = body.substring(firstDot + 1);
      } else {
        minorText = body.substring(firstDot + 1, secondDot);
      }
    }
    Parsed lower = new Parsed(Integer.parseInt(majorText), Integer.parseInt(minorText), 0, null,
            null);
    Parsed upper = (firstDot < 0)
            ? new Parsed(lower.major + 1, 0, 0, null, null)
            : new Parsed(lower.major, lower.minor + 1, 0, null, null);
    return compareParsed(target, lower) >= 0 && compareParsed(target, upper) < 0;
  }

  private static int compareParsed(Parsed a, Parsed b) {
    int cmp = Integer.compare(a.major, b.major);
    if (cmp != 0) {
      return cmp;
    }
    cmp = Integer.compare(a.minor, b.minor);
    if (cmp != 0) {
      return cmp;
    }
    cmp = Integer.compare(a.patch, b.patch);
    if (cmp != 0) {
      return cmp;
    }
    return comparePreRelease(a.preRelease, b.preRelease);
  }

  // --- mode: bump ------------------------------------------------------

  private static @NonNull Result<SemverOut> bumpMode(String version, String bumpType) {
    Parsed current = requireParsed(version);
    String type = (bumpType == null) ? null : bumpType.toLowerCase(Locale.ROOT);
    int newMajor = current.major;
    int newMinor = current.minor;
    int newPatch = current.patch;
    String newPreRelease = null;
    switch (type) {
      case "major" -> {
        newMajor = current.major + 1;
        newMinor = 0;
        newPatch = 0;
      }
      case "minor" -> {
        newMinor = current.minor + 1;
        newPatch = 0;
      }
      case "patch" -> newPatch = current.patch + 1;
      case "prerelease" -> newPreRelease = bumpPreRelease(current.preRelease);
      case null, default -> throw new IllegalArgumentException(
              "semver.bumpType must be one of major, minor, patch, preRelease, was: " + bumpType);
    }
    String result = compose(newMajor, newMinor, newPatch, newPreRelease, null);
    return Result.success(new SemverOut(result));
  }

  private static String bumpPreRelease(String existing) {
    if (existing == null || existing.isEmpty()) {
      throw new IllegalArgumentException(
              "semver.bump.preRelease: version has no prerelease to bump");
    }
    String[] ids = existing.split("\\.");
    for (int i = ids.length - 1; i >= 0; i--) {
      if (isNumericIdentifier(ids[i])) {
        ids[i] = Long.toString(Long.parseLong(ids[i]) + 1L);
        return String.join(".", ids);
      }
    }
    return existing + ".1";
  }

  // --- mode: format ----------------------------------------------------

  private static @NonNull Result<SemverOut> formatMode(
          Integer major, Integer minor, Integer patch, String preRelease, String build) {
    requireNonNegative("major", major);
    requireNonNegative("minor", minor);
    requireNonNegative("patch", patch);
    if (preRelease != null) {
      validateIdentifiers("preRelease", preRelease, true);
    }
    if (build != null) {
      validateIdentifiers("build", build, false);
    }
    String result = compose(major, minor, patch, preRelease, build);
    return Result.success(new SemverOut(result));
  }

  private static void requireNonNegative(String name, Integer value) {
    if (value == null) {
      throw new IllegalArgumentException("semver.format: " + name + " is required");
    }
    if (value < 0) {
      throw new IllegalArgumentException(
              "semver.format: " + name + " must be non-negative, was: " + value);
    }
  }

  // --- helpers ---------------------------------------------------------

  private static String compose(
          int major, int minor, int patch, String preRelease, String build) {
    StringBuilder sb = new StringBuilder();
    sb.append(major).append('.').append(minor).append('.').append(patch);
    if (preRelease != null && !preRelease.isEmpty()) {
      sb.append('-').append(preRelease);
    }
    if (build != null && !build.isEmpty()) {
      sb.append('+').append(build);
    }
    return sb.toString();
  }

  private static Parsed requireParsed(String input) {
    if (input == null) {
      throw new IllegalArgumentException("semver: version is required");
    }
    String stripped = stripLeadingV(input);
    Matcher matcher = SEMVER_PATTERN.matcher(stripped);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("semver: invalid version: " + input);
    }
    int major = Integer.parseInt(matcher.group(1));
    int minor = Integer.parseInt(matcher.group(2));
    int patch = Integer.parseInt(matcher.group(3));
    String preRelease = matcher.group(4);
    String build = matcher.group(5);
    return new Parsed(major, minor, patch, preRelease, build);
  }

  private static String stripLeadingV(String input) {
    if (input.length() > 1 && (input.charAt(0) == 'v' || input.charAt(0) == 'V')) {
      return input.substring(1);
    }
    return input;
  }

  private static void validateIdentifiers(String name, String value, boolean forbidLeadingZeros) {
    if (!value.matches(IDENT_REGEX)) {
      throw new IllegalArgumentException(
              "semver.format: " + name + " has invalid identifier syntax: " + value);
    }
    if (forbidLeadingZeros) {
      for (String id : value.split("\\.")) {
        if (isNumericIdentifier(id) && id.length() > 1 && id.charAt(0) == '0') {
          throw new IllegalArgumentException(
                  "semver.format: " + name + " identifier has leading zero: " + id);
        }
      }
    }
  }

  private record Parsed(int major, int minor, int patch, String preRelease, String build) {
  }
}

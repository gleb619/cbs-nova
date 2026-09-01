package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.RegexIn;
import cbs.nova.starter.helper.model.RegexOut;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.NonNull;

/**
 * Performs regular-expression operations against a string input.
 *
 * <p>
 * The helper supports four {@code op} values (case-insensitive):
 * <ul>
 * <li>{@code "match"}: returns whether the input contains at least one match. This uses
 * {@link Matcher#find()}, so a match anywhere in the input is sufficient; it does not require the
 * entire input to match (which would be {@link Matcher#matches()}).</li>
 * <li>{@code "extract"}: returns the first match, or a specific capturing group of the first match.
 * A missing match is reported as {@code matched=false} with {@code value=null}, not as an
 * error.</li>
 * <li>{@code "replace"}: replaces all matches with the supplied replacement, treated as a literal
 * string so {@code $} and back-reference escapes are not interpreted.</li>
 * <li>{@code "split"}: splits the input around the pattern, preserving trailing empty strings.</li>
 * </ul>
 *
 * <p>
 * Compiled {@link Pattern} instances are cached in a bounded LRU cache (capacity 64) that is shared
 * across invocations.
 */
@Helper(name = "regex")
public class RegexHelper implements Executable<RegexIn, RegexOut> {

  private static final Map<String, Pattern> CACHE = Collections.synchronizedMap(
          new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
              return size() > 64;
            }
          });

  @Override
  public @NonNull Result<RegexOut> execute(@NonNull Context<RegexIn> ctx) {
    try {
      RegexIn input = ctx.body();
      String op = (input.op() == null) ? null : input.op().toLowerCase(Locale.ROOT);
      if (op == null
              || !(op.equals("match")
                      || op.equals("extract")
                      || op.equals("replace")
                      || op.equals("split"))) {
        return Result.failure(
                new IllegalArgumentException("regex.op must be match|extract|replace|split, was: "
                        + input.op()));
      }
      if (input.pattern() == null || input.pattern().isBlank()) {
        return Result.failure(new IllegalArgumentException("regex.pattern is required"));
      }
      if (input.input() == null) {
        return Result.failure(new IllegalArgumentException("regex.input is required"));
      }
      Pattern pattern = compilePattern(input.pattern());
      return switch (op) {
        case "match" -> match(pattern, input.input());
        case "extract" -> extract(pattern, input.input(), input.group(), input.groupName());
        case "replace" -> replace(pattern, input.input(), input.replacement());
        case "split" -> split(pattern, input.input());
        default -> Result.failure(
                new IllegalArgumentException("regex.op must be match|extract|replace|split, was: "
                        + input.op()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static Result<RegexOut> match(Pattern pattern, String input) {
    return Result.success(new RegexOut("match", pattern.matcher(input).find(), null, null));
  }

  private static Result<RegexOut> extract(Pattern pattern, String input, Integer group,
          String groupName) {
    Matcher matcher = pattern.matcher(input);
    if (!matcher.find()) {
      return Result.success(new RegexOut("extract", false, null, null));
    }
    String value;
    if (groupName != null && !groupName.isBlank()) {
      try {
        value = matcher.group(groupName);
      } catch (IllegalArgumentException e) {
        return Result.failure(new IllegalArgumentException(
                "regex.groupName not found in pattern: " + groupName, e));
      }
    } else {
      int idx = (group == null) ? 0 : group;
      if (idx < 0 || idx > matcher.groupCount()) {
        return Result.failure(
                new IllegalArgumentException("regex.group out of range: " + idx));
      }
      value = matcher.group(idx);
    }
    return Result.success(new RegexOut("extract", true, value, null));
  }

  private static Result<RegexOut> replace(Pattern pattern, String input, String replacement) {
    Matcher matcher = pattern.matcher(input);
    String result = matcher
            .replaceAll(Matcher.quoteReplacement(replacement == null ? "" : replacement));
    return Result.success(new RegexOut("replace", null, result, null));
  }

  private static Result<RegexOut> split(Pattern pattern, String input) {
    List<String> values = Arrays.asList(pattern.split(input, -1));
    return Result.success(new RegexOut("split", null, null, values));
  }

  private static Pattern compilePattern(String pattern) {
    Pattern cached;
    synchronized (CACHE) {
      cached = CACHE.get(pattern);
    }
    if (cached != null) {
      return cached;
    }
    try {
      Pattern compiled = Pattern.compile(pattern);
      synchronized (CACHE) {
        CACHE.put(pattern, compiled);
      }
      return compiled;
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("regex.pattern is invalid: " + e.getMessage(), e);
    }
  }

  static int cachedPatternCount() {
    synchronized (CACHE) {
      return CACHE.size();
    }
  }
}

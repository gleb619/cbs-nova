package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.InterpolateIn;
import cbs.nova.starter.helper.model.InterpolateOut;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Safe literal {@code ${key}} string templating — no expression evaluation.
 *
 * <p>
 * Pairs with {@link FormatMessageHelper}, which evaluates the template as a single SpEL expression
 * (powerful but unsafe for operator- or user-supplied templates). Use {@code interpolate} when the
 * template is configuration data that must not be able to call methods.
 *
 * <p>
 * Rules:
 * <ul>
 * <li>{@code ${key}} is substituted from {@code params}; the key is {@code trim()}-ed. Dotted keys
 * walk nested {@code Map<String,Object>} params, e.g. {@code ${order.id}} reads
 * {@code ((Map) params.get("order")).get("id")}.</li>
 * <li>{@code ${key:-default}} uses the literal text after the first {@code :-} as the value when
 * the key is missing. The default is <b>not</b> re-interpolated, so {@code ${x:-${y}}} emits the
 * literal string {@code ${y}} when {@code x} is missing.</li>
 * <li>Default values only apply to missing keys. A key present in the map but mapped to
 * {@code null} renders as {@code ""}, even under {@code onMissing=error} or with a default value.
 * Missing means absent from the map or unreachable because an intermediate path segment is absent
 * or not a map; it does not mean {@code null}-valued.</li>
 * <li>{@code $${} renders a literal {@code $} (i.e. {@code "$${x}"} → {@code "${x}"} when {@code x}
 * is unknown or always produces a literal leading {@code $} when followed by a placeholder).</li>
 * <li>{@code ${}} (empty key) and an unclosed {@code ${} (no matching {@code }}) fail.</li>
 * <li>Braces inside keys are not supported — the first {@code }} closes the placeholder, so
 * {@code ${a${b}} substitutes key {@code a${b}.</li>
 * </ul>
 */
@Helper(name = "interpolate")
public class InterpolateHelper implements Executable<InterpolateIn, InterpolateOut> {

  private static final Object ABSENT = new Object();

  @Override
  public @NonNull Result<InterpolateOut> execute(@NonNull Context<InterpolateIn> ctx) {
    try {
      InterpolateIn input = ctx.body();
      if (input.template() == null) {
        return Result.failure(new IllegalArgumentException("interpolate.template is required"));
      }
      Map<String, Object> params = input.params() == null ? Map.of() : input.params();
      String policy = normalizeOnMissing(input.onMissing());
      if (policy == null) {
        return Result.failure(new IllegalArgumentException(
                "interpolate.onMissing must be 'error', 'empty', or 'keep', was: "
                        + input.onMissing()));
      }

      String template = input.template();
      StringBuilder out = new StringBuilder(template.length());
      Set<String> seenKeys = new LinkedHashSet<>();

      int i = 0;
      int len = template.length();
      while (i < len) {
        char c = template.charAt(i);
        if (c == '$' && i + 1 < len && template.charAt(i + 1) == '$') {
          // '$${' escape: emit literal '${' and skip the leading '$'.
          // If the following char is not '{', still emit both '$' characters literally
          // (we only consumed two chars).
          out.append('$');
          i += 2;
          continue;
        }
        if (c == '$' && i + 1 < len && template.charAt(i + 1) == '{') {
          int close = template.indexOf('}', i + 2);
          if (close < 0) {
            return Result.failure(new IllegalArgumentException(
                    "interpolate: unclosed '${' at offset " + i));
          }
          String rawKey = template.substring(i + 2, close).trim();
          if (rawKey.isEmpty()) {
            return Result.failure(new IllegalArgumentException(
                    "interpolate: empty key at offset " + i));
          }

          int defaultIdx = rawKey.indexOf(":-");
          String keyPath;
          String defaultValue;
          if (defaultIdx >= 0) {
            keyPath = rawKey.substring(0, defaultIdx).trim();
            defaultValue = rawKey.substring(defaultIdx + 2);
          } else {
            keyPath = rawKey;
            defaultValue = null;
          }
          if (keyPath.isEmpty()) {
            return Result.failure(new IllegalArgumentException(
                    "interpolate: empty key at offset " + i));
          }

          Object resolved = resolvePath(params, keyPath.split("\\."));
          if (resolved != ABSENT) {
            out.append(formatValue(resolved));
            seenKeys.add(keyPath);
          } else if (defaultValue != null) {
            out.append(defaultValue);
          } else {
            switch (policy) {
              case "empty" -> out.append("");
              case "keep" -> out.append(template, i, close + 1);
              default -> {
                return Result.failure(new IllegalArgumentException(
                        "interpolate: missing key '" + keyPath + "'"));
              }
            }
          }
          i = close + 1;
          continue;
        }
        out.append(c);
        i++;
      }

      return Result.success(new InterpolateOut(out.toString(), List.copyOf(seenKeys)));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  /**
   * Normalises the {@code onMissing} policy to its lower-case canonical form. Returns
   * {@code "error"} when the input is {@code null} (default), or {@code null} if the value is
   * unrecognised.
   */
  private static String normalizeOnMissing(String raw) {
    if (raw == null) {
      return "error";
    }
    String lower = raw.toLowerCase(Locale.ROOT);
    return switch (lower) {
      case "error", "empty", "keep" -> lower;
      default -> null;
    };
  }

  private static Object resolvePath(Map<String, Object> params, String[] segments) {
    Object current = params;
    for (int i = 0; i < segments.length; i++) {
      if (!(current instanceof Map<?, ?> map)) {
        return ABSENT;
      }
      String segment = segments[i];
      if (!map.containsKey(segment)) {
        return ABSENT;
      }
      current = map.get(segment);
      if (current == null) {
        // A null value anywhere along the path is treated as a present null and renders as empty,
        // not as a missing key, consistent with the flat-key null-vs-absent rule.
        return null;
      }
    }
    return current;
  }

  private static String formatValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros().toPlainString();
    }
    return String.valueOf(value);
  }
}

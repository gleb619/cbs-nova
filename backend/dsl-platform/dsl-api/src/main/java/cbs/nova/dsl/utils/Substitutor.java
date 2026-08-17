package cbs.nova.dsl.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Substitutor {

  private static final @NonNull Pattern DEFAULT_PATTERN = Pattern.compile("[$][{]([^}]+)[}]");

  private Substitutor() {
  }

  public static @NonNull String format(@NonNull String template, @NonNull Map<String, ?> values) {
    return on(template).with(values).render();
  }

  public static @NonNull Builder on(@NonNull String template) {
    return new Builder(template, DEFAULT_PATTERN);
  }

  public static final class Builder {

    private final String template;
    private final Pattern pattern;
    private final Map<String, Object> values = new HashMap<>();
    private @Nullable Function<String, String> missingHandler;

    private Builder(String template, Pattern pattern) {
      this.template = template;
      this.pattern = pattern;
    }

    public @NonNull Builder with(@NonNull String key, @Nullable Object value) {
      values.put(key, value);
      return this;
    }

    public @NonNull Builder with(@NonNull Map<String, ?> values) {
      values.forEach(this.values::put);
      return this;
    }

    public @NonNull Builder missing(@NonNull Function<String, String> handler) {
      this.missingHandler = handler;
      return this;
    }

    public @NonNull String render() {
      var matcher = pattern.matcher(template);
      var sb = new StringBuilder();
      while (matcher.find()) {
        var key = matcher.group(1);
        Object raw;
        if (values.containsKey(key)) {
          raw = values.get(key);
        } else if (missingHandler != null) {
          raw = missingHandler.apply(key);
        } else {
          throw new IllegalArgumentException("Missing placeholder: " + key);
        }
        matcher.appendReplacement(sb, Matcher.quoteReplacement(Objects.toString(raw, "")));
      }
      matcher.appendTail(sb);
      return sb.toString();
    }
  }
}

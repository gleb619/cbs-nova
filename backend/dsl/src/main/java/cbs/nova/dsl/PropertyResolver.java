package cbs.nova.dsl;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

public final class PropertyResolver {
  private final Pattern placeholder;
  private final Map<String, String> properties;
  private final boolean failOnMissing;

  public PropertyResolver(@NonNull Map<String, String> properties, boolean failOnMissing) {
    this.placeholder = Pattern.compile("[$][{]([^}]+)[}]");
    this.properties = Map.copyOf(properties);
    this.failOnMissing = failOnMissing;
  }

  public @NonNull String resolve(@NonNull String input) {
    Matcher m = placeholder.matcher(input);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String key = m.group(1);
      String value = properties.get(key);
      if (value == null) {
        if (failOnMissing) {
          throw new IllegalArgumentException("Unresolved placeholder: " + key);
        }
        value = m.group(0);
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(value));
    }
    m.appendTail(sb);
    return sb.toString();
  }
}

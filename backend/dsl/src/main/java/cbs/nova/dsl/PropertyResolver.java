package cbs.nova.dsl;

import cbs.nova.dsl.utils.Substitutor;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class PropertyResolver {

  private final Map<String, String> properties;
  private final boolean failOnMissing;

  public PropertyResolver(@NonNull Map<String, String> properties, boolean failOnMissing) {
    this.properties = Map.copyOf(properties);
    this.failOnMissing = failOnMissing;
  }

  public @NonNull String resolve(@NonNull String input) {
    return Substitutor.on(input)
            .with(properties)
            .missing(failOnMissing
                    ? key -> {
                      throw new IllegalArgumentException("Unresolved placeholder: " + key);
                    }
                    : key -> "${" + key + "}")
            .render();
  }
}

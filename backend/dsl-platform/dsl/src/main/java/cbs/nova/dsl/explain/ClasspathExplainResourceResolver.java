package cbs.nova.dsl.explain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class ClasspathExplainResourceResolver implements ExplainResourceResolver {

  public static final String DEFAULT_PREFIX = "explain/";

  private final String prefix;

  @Override
  public @NonNull String load(@NonNull String resourcePath) {
    var name = normalize(resourcePath);
    var resource = prefix + name;
    InputStream stream = classLoader().getResourceAsStream(resource);
    if (stream == null) {
      throw new IllegalStateException(
              "Explain resource not found on classpath: " + resource);
    }
    try (stream) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read explain resource: " + resource, ex);
    }
  }

  private @NonNull String normalize(@NonNull String resourcePath) {
    var normalized = resourcePath.startsWith("/")
            ? resourcePath.substring(1)
            : resourcePath;
    if (normalized.startsWith(prefix)) {
      normalized = normalized.substring(prefix.length());
    }
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Explain resource path must not be blank");
    }
    return normalized;
  }

  private static @NonNull ClassLoader classLoader() {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    return contextLoader != null
            ? contextLoader
            : ClasspathExplainResourceResolver.class.getClassLoader();
  }
}

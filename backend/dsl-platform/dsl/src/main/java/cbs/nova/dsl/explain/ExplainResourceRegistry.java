package cbs.nova.dsl.explain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;

/**
 * Holds {@link ExplainResourceProvider} instances discovered via {@link ServiceLoader} and provides
 * two lookups: by metadata {@code name} and by {@code filename} (which matches a DSL object name +
 * {@code .md}).
 */
public final class ExplainResourceRegistry {

  // TODO: Instead add a collection with some memoize via caffeine
  @Deprecated(forRemoval = true)
  private final Map<String, ExplainResourceProvider> byName = new ConcurrentHashMap<>();
  @Deprecated(forRemoval = true)
  private final Map<String, ExplainResourceProvider> byFilename = new ConcurrentHashMap<>();

  public @NonNull ExplainResourceRegistry init(@NonNull ClassLoader classLoader) {
    ServiceLoader.load(ExplainResourceProvider.class, classLoader).forEach(this::register);
    return this;
  }

  public void register(@NonNull ExplainResourceProvider provider) {
    byName.put(provider.name(), provider);
    byFilename.put(provider.filename(), provider);
  }

  public @NonNull Optional<ExplainResourceProvider> findByName(@NonNull String name) {
    return Optional.ofNullable(byName.get(name));
  }

  public @NonNull Optional<ExplainResourceProvider> findByFilename(@NonNull String filename) {
    return Optional.ofNullable(byFilename.get(filename));
  }

  public @NonNull Optional<ExplainResource> describeByName(@NonNull String name) {
    return findByName(name).map(ExplainResourceProvider::resource);
  }

  public @NonNull Optional<ExplainResource> describeByFilename(@NonNull String filename) {
    return findByFilename(filename).map(ExplainResourceProvider::resource);
  }

  public @NonNull List<String> names() {
    return List.copyOf(byName.keySet());
  }

  public @NonNull List<String> filenames() {
    return List.copyOf(byFilename.keySet());
  }
}

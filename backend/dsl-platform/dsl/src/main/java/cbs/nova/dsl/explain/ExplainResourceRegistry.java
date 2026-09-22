package cbs.nova.dsl.explain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.jspecify.annotations.NonNull;

/**
 * Holds {@link ExplainResourceProvider} instances discovered via {@link ServiceLoader} and provides
 * two lookups: by metadata {@code name} and by {@code filename} (which matches a DSL object name +
 * {@code .md}).
 */
public final class ExplainResourceRegistry {

  // Providers are discovered up front via ServiceLoader, so both indexes are memoized once at
  // register time. Bounded size keeps memory under control; the discovered provider set is small,
  // so eviction pressure is negligible in practice.
  private final Cache<String, ExplainResourceProvider> byName = Caffeine.newBuilder()
          .maximumSize(1_024L)
          .build();
  private final Cache<String, ExplainResourceProvider> byFilename = Caffeine.newBuilder()
          .maximumSize(1_024L)
          .build();

  public @NonNull ExplainResourceRegistry init(@NonNull ClassLoader classLoader) {
    ServiceLoader.load(ExplainResourceProvider.class, classLoader).forEach(this::register);
    return this;
  }

  public void register(@NonNull ExplainResourceProvider provider) {
    byName.put(provider.name(), provider);
    byFilename.put(provider.filename(), provider);
  }

  public @NonNull Optional<ExplainResourceProvider> findByName(@NonNull String name) {
    return Optional.ofNullable(byName.getIfPresent(name));
  }

  public @NonNull Optional<ExplainResourceProvider> findByFilename(@NonNull String filename) {
    return Optional.ofNullable(byFilename.getIfPresent(filename));
  }

  public @NonNull Optional<ExplainResource> describeByName(@NonNull String name) {
    return findByName(name).map(ExplainResourceProvider::resource);
  }

  public @NonNull Optional<ExplainResource> describeByFilename(@NonNull String filename) {
    return findByFilename(filename).map(ExplainResourceProvider::resource);
  }

  public @NonNull List<String> names() {
    return List.copyOf(byName.asMap().keySet());
  }

  public @NonNull List<String> filenames() {
    return List.copyOf(byFilename.asMap().keySet());
  }
}

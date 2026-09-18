package cbs.nova.starter.config.properties;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the piece-manifest loader ({@code cbs.dsl.manifest.*}).
 *
 * <p>
 * The loader is enabled by default but is fully inert when the configured path is blank or points
 * to a missing resource: existing deployments without a manifest file start cleanly with an empty
 * snapshot.
 *
 * <p>
 * T549 enforcement knobs:
 * <ul>
 * <li>{@code flags.<name>} — boolean map backing {@code preCheck: feature-flag} evaluation; absent
 * or {@code false} means the flag is disabled (fail-closed).</li>
 * <li>{@code rate-classes.<name>.capacity} / {@code rate-classes.<name>.refill-per-second} — token
 * buckets for {@code preCheck: rate-class}; the guard owns these buckets per principal+piece and
 * they are independent of the global {@code cbs.security.ratelimit.*} filter.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "cbs.dsl.manifest")
public record CbsDslManifestProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("classpath:piece-manifest.yaml") String path,
        Map<String, Boolean> flags,
        Map<String, RateClass> rateClasses) {

  @ConstructorBinding
  public CbsDslManifestProperties {
    path = path == null || path.isBlank() ? "classpath:piece-manifest.yaml" : path;
    flags = flags == null ? Map.of() : Map.copyOf(flags);
    rateClasses = rateClasses == null ? Map.of() : Map.copyOf(rateClasses);
  }

  /** Back-compat convenience constructor: no flags, no rate classes. */
  public CbsDslManifestProperties(boolean enabled, String path) {
    this(enabled, path, null, null);
  }

  /**
   * Token-bucket shape for one named rate class used by {@code preCheck: rate-class}.
   */
  public record RateClass(
          @DefaultValue("20") int capacity,
          @DefaultValue("5.0") double refillPerSecond) {

    public RateClass {
      if (capacity <= 0) {
        capacity = 20;
      }
      if (refillPerSecond <= 0) {
        refillPerSecond = 5.0;
      }
    }
  }
}

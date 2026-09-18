package cbs.nova.starter.config.properties;

import java.time.Duration;
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
 * or {@code false} means the flag is disabled (fail-closed).\u003c/li>
 * <li>{@code rate-classes.<name>.capacity} / {@code rate-classes.<name>.refill-per-second} — token
 * buckets for {@code preCheck: rate-class}; the guard owns these buckets per principal+piece and
 * they are independent of the global {@code cbs.security.ratelimit.*} filter.\u003c/li>
 * </ul>
 *
 * <p>
 * T550 post-check knobs ({@code post-check.*}): executor sizing and {@code block-next-execution}
 * block TTL/scope for the post-check pipeline.
 *
 * <p>
 * T552 object-level enforcement knobs:
 * <ul>
 * <li>{@code object-enforcement.enabled} — master feature flag. Default {@code false}: the guard is
 * a no-op and helper dispatch is byte-for-byte identical to pre-T552 behavior.\u003c/li>
 * <li>{@code object-mode} — production default policy: {@code permissive} (allow unlisted helpers
 * but audit) or {@code strict} (deny-by-default). Preview is always deny-by-default when
 * enforcement is on.\u003c/li>
 * </ul>
 */
@ConfigurationProperties(prefix = "cbs.dsl.manifest")
public record CbsDslManifestProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("classpath:piece-manifest.yaml") String path,
        Map<String, Boolean> flags,
        Map<String, RateClass> rateClasses,
        @DefaultValue PostCheck postCheck,
        @DefaultValue ObjectEnforcement objectEnforcement,
        @DefaultValue("PERMISSIVE") ObjectMode objectMode) {

  @ConstructorBinding
  public CbsDslManifestProperties {
    path = path == null || path.isBlank() ? "classpath:piece-manifest.yaml" : path;
    flags = flags == null ? Map.of() : Map.copyOf(flags);
    rateClasses = rateClasses == null ? Map.of() : Map.copyOf(rateClasses);
    postCheck = postCheck == null ? new PostCheck() : postCheck;
    objectEnforcement = objectEnforcement == null ? new ObjectEnforcement(false)
            : objectEnforcement;
    objectMode = objectMode == null ? ObjectMode.PERMISSIVE : objectMode;
  }

  /** Back-compat convenience constructor: no flags, no rate classes, default post-check tuning. */
  public CbsDslManifestProperties(boolean enabled, String path) {
    this(enabled, path, null, null, null, null, null);
  }

  /** Back-compat convenience constructor: no post-check / object enforcement overrides. */
  public CbsDslManifestProperties(boolean enabled, String path,
          Map<String, Boolean> flags, Map<String, RateClass> rateClasses) {
    this(enabled, path, flags, rateClasses, null, null, null);
  }

  /** Back-compat convenience constructor: post-check override only. */
  public CbsDslManifestProperties(boolean enabled, String path,
          Map<String, Boolean> flags, Map<String, RateClass> rateClasses,
          PostCheck postCheck) {
    this(enabled, path, flags, rateClasses, postCheck, null, null);
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

  /**
   * Post-check pipeline tuning (T550): dedicated executor sizing plus {@code
   * block-next-execution} block TTL and scoping.
   */
  public record PostCheck(
          @DefaultValue("2") int executorPoolSize,
          @DefaultValue("30m") Duration blockTtl,
          @DefaultValue("piece") String blockScope) {

    public PostCheck {
      if (executorPoolSize <= 0) {
        executorPoolSize = 2;
      }
      blockTtl = blockTtl == null || blockTtl.isZero() || blockTtl.isNegative()
              ? Duration.ofMinutes(30)
              : blockTtl;
      if (!"piece".equals(blockScope) && !"principal".equals(blockScope)) {
        blockScope = "piece";
      }
    }

    /** All-defaults convenience constructor (pool 2, TTL 30m, piece scope). */
    public PostCheck() {
      this(2, Duration.ofMinutes(30), "piece");
    }
  }

  /**
   * Master feature flag for T552 object-level capability enforcement.
   */
  public record ObjectEnforcement(@DefaultValue("false") boolean enabled) {

    public ObjectEnforcement {
      // nothing to normalize
    }
  }

  /**
   * Production default policy for unlisted DSL objects. Preview is always deny-by-default when
   * enforcement is enabled.
   */
  public enum ObjectMode {
    STRICT, PERMISSIVE
  }
}

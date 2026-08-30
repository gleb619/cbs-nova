package cbs.nova.starter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Centralised Caffeine cache tuning. Every named runtime cache in the starter looks up its
 * {@link CacheSpec} through {@link #specFor(String)}; caches that don't appear in {@code overrides}
 * fall back to {@link Defaults}.
 *
 * <p>
 * Example YAML:
 *
 * <pre>
 * cbs.nova.cache:
 *   defaults:
 *     ttl: PT10M
 *     max-size: 5000
 *   overrides:
 *     previewResult:
 *       ttl: PT2M
 *       max-size: 500
 * </pre>
 */
@ConfigurationProperties(prefix = "cbs.nova.cache")
@Validated
public record CbsNovaCacheProperties(
        @DefaultValue Defaults defaults,
        @Valid @DefaultValue Map<String, CacheSpec> overrides) {

  public CbsNovaCacheProperties {
    defaults = defaults == null ? new Defaults(Duration.ofMinutes(5), 10_000L) : defaults;
    overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
  }

  /** Well-known cache names exposed for callers and for YAML configuration. */
  public static final class Names {
    public static final String PREVIEW_RESULT = "previewResult";
    public static final String COMPENSATION_MARKERS = "compensationMarkers";
    public static final String UNRELIABLE_API_ATTEMPTS = "unreliableApiAttempts";
    public static final String DRY_RUN_LOG_BUFFERS = "dryRunLogBuffers";
    public static final String RUN_SCOPED_FAKE_CONFIGS = "runScopedFakeConfigs";
    public static final String MAP_INPUT_ADAPTERS = "mapInputAdapters";
    public static final String HELPER_INSTANCE_RESOLUTION = "helperInstanceResolution";
    public static final String INPUT_SCHEMA = "inputSchema";

    private Names() {
    }
  }

  /**
   * Returns the {@link CacheSpec} that applies to the named cache. Falls back to the configured
   * defaults when no override is present.
   */
  public CacheSpec specFor(String name) {
    CacheSpec override = overrides.get(name);
    return override != null ? override : defaults.toCacheSpec();
  }

  public record Defaults(
          @DefaultValue("PT5M") Duration ttl,
          @DefaultValue("10000") @Min(1) long maxSize) {

    CacheSpec toCacheSpec() {
      return new CacheSpec(ttl, maxSize);
    }
  }

  public record CacheSpec(
          @DefaultValue("PT5M") Duration ttl,
          @DefaultValue("10000") @Min(1) long maxSize) {
  }
}

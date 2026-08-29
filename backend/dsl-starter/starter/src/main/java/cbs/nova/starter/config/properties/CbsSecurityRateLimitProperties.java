package cbs.nova.starter.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Opt-in token-bucket rate limiter for mutating DSL REST endpoints.
 *
 * <p>
 * When {@code cbs.security.ratelimit.enabled=false} (the default) no limiting is applied and the
 * starter behaves exactly as before. When enabled, only mutating requests to
 * {@code /api/dsl/run/**}, {@code /api/dsl/preview/**}, {@code /api/dsl/explain/**},
 * {@code /api/dsl/reload}, {@code /api/dsl/drafts/**} and {@code /api/executions/&#42;/cancel} are
 * counted against a per-client-IP token bucket. GET requests and actuator/health paths are always
 * exempt.
 *
 * <p>
 * The bucket is stored in-memory and refilled lazily on each request, so no external runtime
 * dependency is required.
 */
@ConfigurationProperties(prefix = "cbs.security.ratelimit")
public record CbsSecurityRateLimitProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("20") int capacity,
        @DefaultValue("5.0") double refillPerSecond) {

  public CbsSecurityRateLimitProperties {
    if (capacity <= 0) {
      capacity = 20;
    }
    if (refillPerSecond <= 0) {
      refillPerSecond = 5.0;
    }
  }
}

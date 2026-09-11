package cbs.nova.starter.config;

import lombok.AllArgsConstructor;

import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.service.TemporalHealthProbe;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Readiness-only DSL health indicator.
 *
 * <p>
 * Registered by {@link DslHealthIndicatorConfiguration} under the health contributor name
 * {@code dslReadiness} and included in the {@code readiness} health group. It reports the same DSL
 * registry counts and Temporal reachability details as {@link DslHealthIndicator}, but it is the
 * only DSL-related check that can make {@code /actuator/health/readiness} go {@code DOWN}.
 *
 * <p>
 * Liveness is intentionally left to Spring Boot's built-in {@code livenessState} and never consults
 * Temporal, the database, or the DSL registry, so an external dependency outage does not cause
 * orchestrators to restart the pod.
 */
@AllArgsConstructor
public class DslReadinessIndicator implements HealthIndicator {

  private final @Nullable ObjectProvider<TemporalHealthProbe> probeProvider;
  private final @Nullable ObjectProvider<CbsHealthProperties> propsProvider;

  @Override
  public Health health() {
    return DslHealthIndicatorSupport.buildHealth(probeProvider, propsProvider);
  }
}

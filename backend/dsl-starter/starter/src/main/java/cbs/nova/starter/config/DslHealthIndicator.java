package cbs.nova.starter.config;

import lombok.AllArgsConstructor;

import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.service.TemporalHealthProbe;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Generic DSL health indicator contributing to the plain {@code /actuator/health} composite.
 *
 * <p>
 * Registered by {@link DslHealthIndicatorConfiguration}. The actual Temporal reachability / DSL
 * registry logic is shared with {@link DslReadinessIndicator} via {@link DslHealthIndicatorSupport}
 * so that the existing {@code dsl} component keeps its detail keys ({@code processes},
 * {@code transactions}, {@code helpers} and the {@code temporal} block) unchanged.
 *
 * <p>
 * Readiness-specific signal lives in {@link DslReadinessIndicator}; liveness relies on Spring
 * Boot's built-in {@code livenessState} only.
 */
@AllArgsConstructor
public class DslHealthIndicator implements HealthIndicator {

  private final @Nullable ObjectProvider<TemporalHealthProbe> probeProvider;
  private final @Nullable ObjectProvider<CbsHealthProperties> propsProvider;

  @Override
  public Health health() {
    return DslHealthIndicatorSupport.buildHealth(probeProvider, propsProvider);
  }
}

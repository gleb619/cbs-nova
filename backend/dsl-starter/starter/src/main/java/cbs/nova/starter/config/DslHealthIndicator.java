package cbs.nova.starter.config;

import lombok.AllArgsConstructor;

import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.service.TemporalHealthProbe;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

@AllArgsConstructor
public class DslHealthIndicator implements HealthIndicator {

  private final @Nullable ObjectProvider<TemporalHealthProbe> probeProvider;
  private final @Nullable ObjectProvider<CbsHealthProperties> propsProvider;

  @Override
  public Health health() {
    return DslHealthIndicatorSupport.buildHealth(probeProvider, propsProvider);
  }
}

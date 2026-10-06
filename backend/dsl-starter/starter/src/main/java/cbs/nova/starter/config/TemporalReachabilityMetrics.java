package cbs.nova.starter.config;

import cbs.nova.starter.service.TemporalHealthProbe;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Publishes a {@code dsl_temporal_reachable} gauge (1 = reachable, 0 = unreachable) that reuses the
 * same {@link TemporalHealthProbe} used by the DSL health/readiness indicators.
 */
public class TemporalReachabilityMetrics {

  public static final String GAUGE_NAME = "dsl_temporal_reachable";

  private final TemporalHealthProbe probe;

  public TemporalReachabilityMetrics(TemporalHealthProbe probe, MeterRegistry meterRegistry) {
    this.probe = probe;
    Gauge.builder(GAUGE_NAME, this, m -> m.probe.probe().reachable() ? 1.0 : 0.0)
            .description("1 if Temporal is reachable, 0 otherwise")
            .register(meterRegistry);
  }
}

package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.service.TemporalHealthProbe;
import cbs.nova.starter.service.TemporalHealthProbe.TemporalHealth;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TemporalReachabilityMetricsTest {

  @Test
  void gaugeFlipsBetweenOneAndZeroWithReachability() {
    TemporalHealthProbe probe = mock(TemporalHealthProbe.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    when(probe.probe()).thenReturn(TemporalHealth.reachable("127.0.0.1:7233"));
    new TemporalReachabilityMetrics(probe, registry);

    Gauge gauge = registry.find(TemporalReachabilityMetrics.GAUGE_NAME).gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(1.0);

    when(probe.probe()).thenReturn(TemporalHealth.unreachable("127.0.0.1:7233", "refused"));
    assertThat(gauge.value()).isEqualTo(0.0);
  }
}

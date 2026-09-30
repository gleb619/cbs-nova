package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * T744 wiring spec. Asserts the three concrete pre-conditions of the OTLP export path:
 *
 * <ol>
 * <li>The full starter context refreshes with both OTLP knobs on (no missing-bean failures, no port
 * collisions, no bean cycles).</li>
 * <li>An {@link OpenTelemetry} bean is wired and is NOT the no-op instance — i.e. the starter-owned
 * SDK is actually built when the OTLP endpoint resolves.</li>
 * <li>{@link OtlpMeterRegistry} participates in the Micrometer composite, so every existing meter
 * (dsl.preview.duration, dsl.run.duration, etc.) is published over OTLP without any registration
 * change.</li>
 * </ol>
 *
 * <p>
 * The collector is intentionally unreachable from this test; OTLP exporters buffer and retry with a
 * single WARN, which is exactly the inert-by-default contract documented in
 * {@code application.yml}. Endpoint resolution itself is covered by
 * {@code TracingConfigurationTest}.
 */
@SpringBootTest(classes = cbs.nova.starter.NotificationTestApplication.class, properties = {
    // Activate the starter's trace SDK through the cbs-namespaced knob (the test mirrors
    // operator usage; the Spring Boot standard property is covered by TracingConfigurationTest).
    "cbs.nova.tracing.otlp.endpoint=http://localhost:4318/v1/traces",
    // Activate Spring Boot's OTLP metrics auto-config; both knobs are needed because the
    // enabled flag is @ConditionalOnEnabledMetricsExport("otlp").
    "management.otlp.metrics.export.enabled=true",
    "management.otlp.metrics.export.url=http://localhost:4318/v1/metrics",
    // Turn the per-job worker + Temporal wires off so the context isn't blocked on them.
    "cbs.dsl.worker.enabled=false",
    "cbs.nova.events.mq.enabled=false"
})
class OtlpExportWiringTest {

  @Autowired
  ApplicationContext context;

  @Autowired
  OpenTelemetry openTelemetry;

  @Autowired
  MeterRegistry meterRegistry;

  @Test
  void contextStartsWithBothExportersWired() {
    // @SpringBootTest fails fast if the context can't refresh; reaching this assertion is the
    // proof that no OTLP wiring change introduced a missing-bean or cycle. The three remaining
    // @Autowired fields (OpenTelemetry, MeterRegistry, ApplicationContext) already prove the
    // context is live — null/empty defaults would have thrown at injection time.
    assertThat(context.getBeanDefinitionNames())
            .as("auto-configured OTLP beans should be present in the live context")
            .anyMatch(name -> name.contains("OtlpMeterRegistry")
                    || name.contains("otlpMeterRegistry"));
  }

  @Test
  void openTelemetryBeanIsNotNoop() {
    assertThat(openTelemetry)
            .as("starter-owned OpenTelemetry should be the real SDK when an endpoint resolves")
            .isNotSameAs(OpenTelemetry.noop());
  }

  @Test
  void otlpMeterRegistryParticipatesInComposite() {
    OtlpMeterRegistry otlp = context.getBean(OtlpMeterRegistry.class);
    assertThat(otlp)
            .as("OtlpMeterRegistry must be auto-configured so existing meters flow over OTLP")
            .isNotNull();
    // Spring Boot wraps the autowired MeterRegistry in a CompositeMeterRegistry that delegates
    // to every registered child registry. Asserting the OTLP registry is one of them is the
    // public-API signal that existing meters (dsl.preview.duration, dsl.run.duration, ...)
    // are exported without code changes.
    assertThat(meterRegistry)
            .as("autowired MeterRegistry must be the Spring Boot composite")
            .isInstanceOf(CompositeMeterRegistry.class);
    assertThat(((CompositeMeterRegistry) meterRegistry).getRegistries())
            .as("OtlpMeterRegistry must register itself in the composite MeterRegistry")
            .contains(otlp);
  }
}

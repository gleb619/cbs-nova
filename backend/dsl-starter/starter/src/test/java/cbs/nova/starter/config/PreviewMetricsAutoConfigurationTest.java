package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.metrics.PreviewMetricsCollector;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the wiring of {@link PreviewMetricsAutoConfiguration} without booting a full Spring
 * Boot application.
 *
 * <p>This auto-configuration declares no {@code @Conditional*} annotations at all — it is wired
 * unconditionally when its imports file entry is processed. The {@link MeterBinder} it contributes
 * registers two gauges against any {@link MeterRegistry} bean present in the context. Tests
 * therefore confirm that the bean is always present and that binding it to a {@link MeterRegistry}
 * registers the expected gauges.
 */
class PreviewMetricsAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PreviewMetricsAutoConfiguration.class));

  @BeforeEach
  void resetSharedSnapshot() {
    // PreviewMetricsCollector stores its latest snapshot in a static volatile field. Without this
    // reset, a previous test that produced a snapshot would leak its values into the gauge lambda.
    PreviewMetricsCollector.resetLatestSnapshot();
  }

  @AfterEach
  void clearSharedSnapshotAfter() {
    PreviewMetricsCollector.resetLatestSnapshot();
  }

  @Test
  void meterBinderBeanIsAlwaysWired() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(MeterBinder.class);
      assertThat(ctx.getBean(MeterBinder.class)).isNotNull();
    });
  }

  @Test
  void meterBinderRegistersExpectedGaugesWhenMeterRegistryPresent() {
    runner.withUserConfiguration(MeterRegistryConfiguration.class).run(ctx -> {
      assertThat(ctx).hasSingleBean(MeterBinder.class);
      assertThat(ctx).hasSingleBean(MeterRegistry.class);

      MeterRegistry registry = ctx.getBean(MeterRegistry.class);
      // Bind the binder manually — ApplicationContextRunner does not invoke MeterBinder.bindTo
      // automatically. The binder is the lambda created inside previewMetricsBinder().
      MeterBinder binder = ctx.getBean(MeterBinder.class);
      binder.bindTo(registry);

      Gauge duration = registry.find("cbs.nova.preview.execution.duration").gauge();
      Gauge memory = registry.find("cbs.nova.preview.memory.used").gauge();
      assertThat(duration).as("execution duration gauge must be registered").isNotNull();
      assertThat(memory).as("memory used gauge must be registered").isNotNull();
      // With no metrics collected yet, both gauges report 0.0.
      assertThat(duration.value()).isEqualTo(0.0);
      assertThat(memory.value()).isEqualTo(0.0);
    });
  }

  @Test
  void meterBinderBeanTypeIsStableAcrossContexts() {
    // Two independent runner invocations must each get exactly one MeterBinder instance.
    runner.run(ctx -> assertThat(ctx).hasSingleBean(MeterBinder.class));
    runner.run(ctx -> assertThat(ctx).hasSingleBean(MeterBinder.class));
  }

  @Configuration
  static class MeterRegistryConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}

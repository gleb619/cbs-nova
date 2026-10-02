package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OpenTelemetryHelperPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(OpenTelemetryHelperPropertiesConfiguration.class);

  @Test
  void defaultsAreApplied() {
    runner.run(ctx -> {
      OpenTelemetryHelperProperties p = ctx.getBean(OpenTelemetryHelperProperties.class);
      assertThat(p.spanMaxEntries()).isEqualTo(1000);
      assertThat(p.spanTtl()).isEqualTo(Duration.ofMinutes(10));
      assertThat(p.baggageTtl()).isEqualTo(Duration.ofMinutes(10));
      assertThat(p.baggageMaxRuns()).isEqualTo(1000);
    });
  }

  @Test
  void customValuesAreBound() {
    runner
            .withPropertyValues(
                    "cbs.otel.helper.span-max-entries=42",
                    "cbs.otel.helper.span-ttl=PT5M",
                    "cbs.otel.helper.baggage-ttl=PT30M",
                    "cbs.otel.helper.baggage-max-runs=7")
            .run(ctx -> {
              OpenTelemetryHelperProperties p = ctx.getBean(OpenTelemetryHelperProperties.class);
              assertThat(p.spanMaxEntries()).isEqualTo(42);
              assertThat(p.spanTtl()).isEqualTo(Duration.ofMinutes(5));
              assertThat(p.baggageTtl()).isEqualTo(Duration.ofMinutes(30));
              assertThat(p.baggageMaxRuns()).isEqualTo(7);
            });
  }

  @Test
  void invalidSpanMaxEntriesBelowOneFailsBinding() {
    runner
            .withPropertyValues("cbs.otel.helper.span-max-entries=0")
            .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void invalidBaggageMaxRunsBelowOneFailsBinding() {
    runner
            .withPropertyValues("cbs.otel.helper.baggage-max-runs=0")
            .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Configuration
  @EnableConfigurationProperties(OpenTelemetryHelperProperties.class)
  static class OpenTelemetryHelperPropertiesConfiguration {
  }
}

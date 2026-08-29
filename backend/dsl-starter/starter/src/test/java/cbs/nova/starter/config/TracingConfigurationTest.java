package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.tracing.OpenTelemetryContextPropagator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TracingConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TracingConfiguration.class));

  @Test
  void tracingIsNoOpByDefault() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(OpenTelemetry.class);
      OpenTelemetry openTelemetry = ctx.getBean(OpenTelemetry.class);
      assertThat(openTelemetry).isSameAs(OpenTelemetry.noop());

      Span span = openTelemetry.getTracer("test").spanBuilder("noop-span").startSpan();
      span.end();
      assertThat(span.getSpanContext().isValid()).isFalse();
    });
  }

  @Test
  void propagatorBeanIsAlwaysWired() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(OpenTelemetryContextPropagator.class);
    });
  }

  @Test
  void blankEndpointKeepsTracingNoOp() {
    runner
            .withPropertyValues("cbs.nova.tracing.otlp.endpoint=")
            .run(ctx -> {
              OpenTelemetry openTelemetry = ctx.getBean(OpenTelemetry.class);
              assertThat(openTelemetry).isSameAs(OpenTelemetry.noop());
            });
  }
}

package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.TracingProperties;
import cbs.nova.starter.tracing.OpenTelemetryContextPropagator;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class TracingConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TracingConfiguration.class));

  @Test
  void tracingIsNoOpByDefault() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(OpenTelemetry.class);
      OpenTelemetry openTelemetry = ctx.getBean(OpenTelemetry.class);
      assertThat(openTelemetry).isSameAs(OpenTelemetry.noop());

      io.opentelemetry.api.trace.Span span = openTelemetry.getTracer("test")
              .spanBuilder("noop-span").startSpan();
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

  @Test
  void propertyEndpointOverridesEnvVariable() {
    var properties = new TracingProperties(null,
            new TracingProperties.Otlp("http://property.example:4318/v1/traces"));
    var environment = new MockEnvironment()
            .withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://env.example:4318/v1/traces");

    assertThat(TracingConfiguration.resolveOtlpEndpoint(properties, environment))
            .isEqualTo("http://property.example:4318/v1/traces");
  }

  @Test
  void envFallbackIsUsedWhenPropertyEndpointIsBlank() {
    var properties = new TracingProperties(null, new TracingProperties.Otlp(""));
    var environment = new MockEnvironment()
            .withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://env.example:4318/v1/traces");

    assertThat(TracingConfiguration.resolveOtlpEndpoint(properties, environment))
            .isEqualTo("http://env.example:4318/v1/traces");
  }

  @Test
  void customServiceNameIsBound() {
    runner
            .withPropertyValues("cbs.nova.tracing.service-name=custom-service")
            .run(ctx -> {
              TracingProperties properties = ctx.getBean(TracingProperties.class);
              assertThat(properties.serviceName()).isEqualTo("custom-service");
            });
  }
}

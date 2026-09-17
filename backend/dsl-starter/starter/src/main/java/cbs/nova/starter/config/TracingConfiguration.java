package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.TracingProperties;
import cbs.nova.starter.tracing.OpenTelemetryContextPropagator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(TracingProperties.class)
public class TracingConfiguration {

  @Bean
  @ConditionalOnMissingBean
  OpenTelemetry openTelemetry(
          TracingProperties tracingProperties,
          @Autowired Environment environment) {
    String endpoint = resolveOtlpEndpoint(tracingProperties, environment);
    if (!StringUtils.hasText(endpoint)) {
      return OpenTelemetry.noop();
    }

    OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .build();

    Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                    AttributeKey.stringKey("service.name"), tracingProperties.serviceName())));

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(resource)
            .build();

    return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
  }

  /**
   * Resolves the OTLP endpoint in T519 order: application property, then empty default, then the
   * {@code OTEL_EXPORTER_OTLP_ENDPOINT} environment variable.
   */
  static String resolveOtlpEndpoint(TracingProperties tracingProperties, Environment environment) {
    String endpoint = tracingProperties.otlp().endpoint();
    if (!StringUtils.hasText(endpoint)) {
      endpoint = environment.getProperty("OTEL_EXPORTER_OTLP_ENDPOINT");
    }
    return endpoint;
  }

  @Bean
  @ConditionalOnMissingBean
  OpenTelemetryContextPropagator openTelemetryContextPropagator(OpenTelemetry openTelemetry) {
    return new OpenTelemetryContextPropagator(openTelemetry);
  }
}

package cbs.nova.starter.config;

import static cbs.nova.starter.core.StarterConstants.OTEL_EXPORTER_OTLP_ENDPOINT;
import static cbs.nova.starter.core.StarterConstants.OTEL_SERVICE_NAME;

import cbs.nova.starter.core.StarterConstants;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class TracingConfiguration {

  @Bean
  @ConditionalOnMissingBean
  OpenTelemetry openTelemetry(
          @Value("${cbs.nova.tracing.otlp.endpoint:}") String configuredEndpoint,
          @Autowired Environment environment) {
    String endpoint = configuredEndpoint;
    if (!StringUtils.hasText(endpoint)) {
      // TODO: redo with an app.yml setting instead of env hardcode
      endpoint = environment.getProperty(OTEL_EXPORTER_OTLP_ENDPOINT);
    }
    if (!StringUtils.hasText(endpoint)) {
      return OpenTelemetry.noop();
    }

    OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .build();

    Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                    AttributeKey.stringKey("service.name"), OTEL_SERVICE_NAME)));

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(resource)
            .build();

    return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
  }

  @Bean
  @ConditionalOnMissingBean
  OpenTelemetryContextPropagator openTelemetryContextPropagator(OpenTelemetry openTelemetry) {
    return new OpenTelemetryContextPropagator(openTelemetry);
  }
}

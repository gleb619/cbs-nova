package cbs.nova.starter.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * T842 wires backend logs to Loki through the otel-collector logs pipeline. Aligned with
 * OpenTelemetryAppender install hook.
 */
@Configuration
public class OtlpLoggingConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(OtlpLoggingConfiguration.class);

  private static final String APPENDER_NAME = "OpenTelemetry";

  private static final String OTLP_ENDPOINT_PROPERTY = "otel.exporter.otlp.endpoint";
  private static final String OTLP_ENDPOINT_ENV = "OTEL_EXPORTER_OTLP_ENDPOINT";
  private static final String SERVICE_NAME_PROPERTY = "otel.service.name";
  private static final String SERVICE_NAME_ENV = "OTEL_SERVICE_NAME";

  private final Environment environment;

  public OtlpLoggingConfiguration(Environment environment) {
    this.environment = environment;
  }

  /**
   * Runs aftercontext is wired so any{@code otel.service.name} configuredy Spring (.g. via
   * {@spring.application.name} when the operator opts inis visible to the installer.
   */
  @org.springframework.context.annotation.Bean
  ApplicationRunner otlpLogAppenderInstaller() {
    return _ -> installIfEnabled();
  }

  void installIfEnabled() {
    String endpoint = resolveEndpoint();
    if (!StringUtils.hasText(endpoint)) {
      LOG.info("OTLP log appender disabled: no OTLP endpoint configured (set {} or {} to enable)",
              OTLP_ENDPOINT_PROPERTY, OTLP_ENDPOINT_ENV);
      return;
    }

    String serviceName = resolveServiceName();
    Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
            AttributeKey.stringKey("service.name"), serviceName)));

    OtlpHttpLogRecordExporter exporter = OtlpHttpLogRecordExporter.builder()
            .setEndpoint(endpoint)
            .build();
    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).build())
            .setResource(resource)
            .build();
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setLoggerProvider(loggerProvider)
            .buildAndRegisterGlobal();

    OpenTelemetryAppender.install(sdk);
    LOG.info("OTLP log appender enabled: endpoint={} service.name={} appender={}",
            endpoint, serviceName, APPENDER_NAME);
  }

  private String resolveEndpoint() {
    String endpoint = environment.getProperty(OTLP_ENDPOINT_PROPERTY);
    if (!StringUtils.hasText(endpoint)) {
      endpoint = System.getenv(OTLP_ENDPOINT_ENV);
    }
    return endpoint;
  }

  private String resolveServiceName() {
    String name = environment.getProperty(SERVICE_NAME_PROPERTY);
    if (!StringUtils.hasText(name)) {
      name = System.getenv(SERVICE_NAME_ENV);
    }
    if (!StringUtils.hasText(name)) {
      name = environment.getProperty("spring.application.name");
    }
    return StringUtils.hasText(name) ? name : "cbs-nova";
  }
}

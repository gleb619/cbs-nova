package cbs.nova.starter.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for OpenTelemetry tracing identity and export.
 *
 * <p>
 * The OTLP endpoint follows the resolution order required by T519:
 * {@code cbs.nova.tracing.otlp.endpoint} property -> default (empty) ->
 * {@code OTEL_EXPORTER_OTLP_ENDPOINT} environment variable -> disabled (noop).
 */
@ConfigurationProperties(prefix = "cbs.nova.tracing")
@Validated
public record TracingProperties(
        @NotBlank @DefaultValue("cbs-nova") String serviceName,
        @Valid @DefaultValue Otlp otlp) {

  public TracingProperties {
    serviceName = (serviceName == null || serviceName.isBlank()) ? "cbs-nova" : serviceName;
    otlp = otlp == null ? new Otlp("") : otlp;
  }

  public record Otlp(
          /**
           * OTLP HTTP trace endpoint. Leave blank to fall back to the
           * {@code OTEL_EXPORTER_OTLP_ENDPOINT} environment variable, or to disable tracing if
           * neither is set.
           */
          @DefaultValue("") String endpoint) {
  }
}

package cbs.nova.starter.config;

import cbs.nova.starter.metrics.PreviewMetricsCollector;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class PreviewMetricsAutoConfiguration {

  @Bean
  MeterBinder previewMetricsBinder() {
    return registry -> {
      Gauge.builder("cbs.nova.preview.execution.duration", () -> {
        var s = PreviewMetricsCollector.getLatestSnapshot();
        return s != null ? s.executionDurationMs() : 0.0;
      }).description("Last preview execution duration in ms").register(registry);

      Gauge.builder("cbs.nova.preview.memory.used", () -> {
        var s = PreviewMetricsCollector.getLatestSnapshot();
        return s != null ? s.memoryUsedBytes() : 0.0;
      }).description("Last preview memory delta in bytes").register(registry);
    };
  }
}

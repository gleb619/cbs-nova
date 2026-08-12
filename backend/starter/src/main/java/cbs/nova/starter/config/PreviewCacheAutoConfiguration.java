package cbs.nova.starter.config;

import cbs.nova.starter.cache.PreviewResultCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
// TODO: replace with a configuration properties record instead
public class PreviewCacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  PreviewResultCache previewResultCache(
          @Value("${cbs.nova.preview.cache.ttlMs:300000}") long ttlMs) {
    return new PreviewResultCache(ttlMs);
  }

  @Bean
  MeterBinder previewCacheMetricsBinder(PreviewResultCache previewResultCache) {
    return registry -> {
      Gauge.builder("cbs.nova.preview.cache.hit.count", previewResultCache,
              cache -> cache.getStats().getOrDefault("hits", 0L).doubleValue())
              .description("Number of preview cache hits")
              .register(registry);

      Gauge.builder("cbs.nova.preview.cache.miss.count", previewResultCache,
              cache -> cache.getStats().getOrDefault("misses", 0L).doubleValue())
              .description("Number of preview cache misses")
              .register(registry);
    };
  }
}

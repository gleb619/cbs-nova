package cbs.nova.starter.config;

import cbs.nova.starter.service.PreviewResultCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableConfigurationProperties(CbsNovaPreviewProperties.class)
public class PreviewCacheConfiguration {

  @Bean
  @ConditionalOnMissingBean
  PreviewResultCache previewResultCache(CbsNovaPreviewProperties properties) {
    return new PreviewResultCache(properties.cache().ttlMs());
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

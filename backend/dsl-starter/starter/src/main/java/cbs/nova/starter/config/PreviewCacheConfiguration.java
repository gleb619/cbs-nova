package cbs.nova.starter.config;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.PreviewModels.PreviewCacheKey;
import cbs.nova.starter.service.PreviewResultCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({CbsNovaPreviewProperties.class, CbsNovaCacheProperties.class})
public class PreviewCacheConfiguration {

  @Bean
  @ConditionalOnMissingBean
  PreviewResultCache previewResultCache(
          CbsNovaPreviewProperties properties, CbsNovaCacheProperties cacheProperties) {
    var spec = cacheProperties.specFor(StarterConstants.PREVIEW_RESULT);
    Cache<PreviewCacheKey, PreviewReport> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(properties.cache().ttlMs()))
            .maximumSize(spec.maxSize())
            .recordStats()
            .build();
    return new PreviewResultCache(cache);
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

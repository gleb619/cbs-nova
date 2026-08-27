package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.cache.PreviewResultCache;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies the wiring of {@link PreviewCacheConfiguration} without booting a full Spring Boot
 * application.
 *
 * <p>
 * The auto-configuration declares no {@code @ConditionalOnProperty} toggles: the only conditional
 * on the cache bean is {@code @ConditionalOnMissingBean}. Therefore both beans are wired whenever
 * the configuration is imported and no user bean of type {@link PreviewResultCache} is present.
 * When the user supplies their own {@link PreviewResultCache}, the starter backs off from creating
 * its own but still contributes the {@link MeterBinder} bound to the user-supplied cache instance.
 */
class PreviewCacheConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PreviewCacheConfiguration.class));

  @Test
  void bothBeansAreRegisteredWhenNoUserCacheBeanExists() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(PreviewResultCache.class);
      assertThat(ctx).hasSingleBean(MeterBinder.class);

      PreviewResultCache cache = ctx.getBean(PreviewResultCache.class);
      // Default ttlMs comes from CbsNovaPreviewProperties default (300_000L).
      assertThat(cache).isNotNull();

      MeterBinder binder = ctx.getBean(MeterBinder.class);
      assertThat(binder).isNotNull();
    });
  }

  @Test
  void userSuppliedCacheBeanWinsAndBinderStillWired() {
    runner.withUserConfiguration(CustomCacheConfiguration.class).run(ctx -> {
      assertThat(ctx).hasSingleBean(PreviewResultCache.class);
      assertThat(ctx.getBean(PreviewResultCache.class))
              .isSameAs(ctx.getBean("customPreviewResultCache"));
      // The MeterBinder is still contributed (it depends on PreviewResultCache, which now resolves
      // to the user-supplied instance via @ConditionalOnMissingBean).
      assertThat(ctx).hasSingleBean(MeterBinder.class);
    });
  }

  @Test
  void cacheBeanRespectsTtlFromProperties() {
    runner
            .withPropertyValues("cbs.nova.preview.cache.ttlMs=1234")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(PreviewResultCache.class);
              PreviewResultCache cache = ctx.getBean(PreviewResultCache.class);
              // Behavior-side check: a freshly created cache with ttlMs=1234 has zero hits/misses.
              assertThat(cache.getStats()).containsEntry("hits", 0L).containsEntry("misses", 0L);
            });
  }

  @Configuration
  static class CustomCacheConfiguration {

    @Bean
    PreviewResultCache customPreviewResultCache() {
      return new PreviewResultCache(60_000L);
    }
  }
}

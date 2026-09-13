package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.service.TemporalHealthProbe;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HealthIndicator.class)
public class DslHealthIndicatorConfiguration {

  @Bean
  DslHealthIndicator dslHealthIndicator(
          @Nullable ObjectProvider<TemporalHealthProbe> probeProvider,
          @Nullable ObjectProvider<CbsHealthProperties> propsProvider) {
    return new DslHealthIndicator(probeProvider, propsProvider);
  }

  @Bean
  DslReadinessIndicator dslReadiness(
          @Nullable ObjectProvider<TemporalHealthProbe> probeProvider,
          @Nullable ObjectProvider<CbsHealthProperties> propsProvider) {
    return new DslReadinessIndicator(probeProvider, propsProvider);
  }
}

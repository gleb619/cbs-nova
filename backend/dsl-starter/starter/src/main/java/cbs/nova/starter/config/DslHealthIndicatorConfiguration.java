package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.service.TemporalHealthProbe;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the DSL health indicators only when Spring Boot actuator's {@link HealthIndicator}
 * is on the classpath.
 *
 * <p>Both indicators are explicit beans so they are available in every auto-configured
 * application context (including tests that do not component-scan {@code cbs.nova.starter.config}).
 * The bean names are chosen so that Spring Boot's health contributor name generator produces
 * {@code dsl} and {@code dslReadiness}, matching the readiness group include list in
 * {@code application.yml}.
 */
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

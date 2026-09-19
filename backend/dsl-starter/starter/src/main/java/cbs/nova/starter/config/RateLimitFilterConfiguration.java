package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.ratelimit.RateLimitStore;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.web.RateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(CbsSecurityRateLimitProperties.class)
public class RateLimitFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean(RateLimitFilter.class)
  public RateLimitFilter rateLimitFilter(CbsSecurityRateLimitProperties properties,
          ObjectMapper objectMapper,
          RateLimitStore rateLimitStore,
          ObjectProvider<ApiKeyStore> apiKeyStoreProvider) {
    ApiKeyStore apiKeyStore = apiKeyStoreProvider.getIfAvailable();
    return new RateLimitFilter(properties, objectMapper, rateLimitStore, apiKeyStore);
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
          RateLimitFilter rateLimitFilter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
            rateLimitFilter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}

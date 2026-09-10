package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.web.ApiKeyAuthFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(prefix = "cbs.dsl.auth", name = "enabled", havingValue = "true")
public class ApiKeyAuthFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ApiKeyAuthFilter apiKeyAuthFilter(
          DslProperties dslProperties,
          ObjectProvider<ApiKeyStore> apiKeyStoreProvider,
          ObjectMapper objectMapper) {
    ApiKeyStore store = apiKeyStoreProvider.getIfAvailable();
    return new ApiKeyAuthFilter(dslProperties.auth().apiKey(), store, objectMapper);
  }

  @Bean
  public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(
          ApiKeyAuthFilter apiKeyAuthFilter) {
    FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(
            apiKeyAuthFilter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}

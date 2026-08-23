package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.web.ApiKeyAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
public class ApiKeyAuthFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ApiKeyAuthFilter apiKeyAuthFilter(DslProperties dslProperties, ObjectMapper objectMapper) {
    return new ApiKeyAuthFilter(dslProperties.auth().apiKey(), objectMapper);
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
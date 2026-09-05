package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.web.ApiKeyAuthFilter;
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
  public ApiKeyAuthFilter apiKeyAuthFilter(DslProperties dslProperties, ObjectMapper objectMapper) {
    return new ApiKeyAuthFilter(dslProperties.getAuth().getApiKey(), objectMapper);
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

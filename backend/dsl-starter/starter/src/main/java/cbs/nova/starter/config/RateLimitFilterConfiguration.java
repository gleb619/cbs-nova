package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.web.RateLimitFilter;
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
  @ConditionalOnMissingBean
  public RateLimitFilter rateLimitFilter(CbsSecurityRateLimitProperties properties,
          ObjectMapper objectMapper) {
    return new RateLimitFilter(properties, objectMapper);
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

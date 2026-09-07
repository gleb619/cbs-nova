package cbs.nova.starter.config;

import cbs.nova.starter.web.RequestIdFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RequestIdFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RequestIdFilter requestIdFilter(ObjectMapper objectMapper) {
    return new RequestIdFilter(objectMapper);
  }

  @Bean
  public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
          RequestIdFilter requestIdFilter) {
    FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(
            requestIdFilter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}

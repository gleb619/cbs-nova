package cbs.nova.starter.config;

import cbs.nova.starter.web.RequestIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@AutoConfiguration
public class RequestIdFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
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

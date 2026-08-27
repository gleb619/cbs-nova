package cbs.nova.starter.config;

import cbs.nova.starter.error.DefaultDslExceptionMapper;
import cbs.nova.starter.error.DslExceptionMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@Configuration
public class DslErrorHandlingConfiguration {

  @Bean
  @ConditionalOnMissingBean(DslExceptionMapper.class)
  public DslExceptionMapper dslExceptionMapper() {
    return new DefaultDslExceptionMapper();
  }
}

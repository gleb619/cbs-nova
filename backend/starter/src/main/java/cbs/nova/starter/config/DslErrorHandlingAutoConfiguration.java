package cbs.nova.starter.config;

import cbs.nova.starter.error.DefaultDslExceptionMapper;
import cbs.nova.starter.error.DslExceptionMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the default {@link DslExceptionMapper} when the host application has not provided its
 * own bean. Host apps can override the mapper by declaring their own
 * {@code @Bean DslExceptionMapper}; the {@link ConditionalOnMissingBean} guard ensures their
 * implementation wins.
 */
@AutoConfiguration
public class DslErrorHandlingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(DslExceptionMapper.class)
  public DslExceptionMapper dslExceptionMapper() {
    return new DefaultDslExceptionMapper();
  }
}

package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.logging.LoggingExecutionListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CbsNovaLoggingProperties.class)
public class LoggingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LoggingExecutionListener loggingExecutionListener(
          CbsNovaLoggingProperties properties) {
    return new LoggingExecutionListener(properties);
  }
}

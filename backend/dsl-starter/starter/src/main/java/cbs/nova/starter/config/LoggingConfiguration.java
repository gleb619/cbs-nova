package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.logging.LoggingExecutionListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableConfigurationProperties(CbsNovaLoggingProperties.class)
public class LoggingConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LoggingExecutionListener loggingExecutionListener(
          CbsNovaLoggingProperties properties) {
    return new LoggingExecutionListener(properties);
  }
}

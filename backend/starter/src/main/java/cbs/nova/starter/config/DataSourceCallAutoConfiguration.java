package cbs.nova.starter.config;

import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.capture.DataSourceProxyBeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "javax.sql.DataSource")
public class DataSourceCallAutoConfiguration {

  @Bean
  DataSourceProxyBeanPostProcessor dataSourceProxyBeanPostProcessor(
          ExternalCallTracker externalCallTracker) {
    return new DataSourceProxyBeanPostProcessor(externalCallTracker);
  }
}

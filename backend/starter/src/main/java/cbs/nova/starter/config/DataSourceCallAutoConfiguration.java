package cbs.nova.starter.config;

import cbs.nova.starter.capture.DataSourceProxyBeanPostProcessor;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "javax.sql.DataSource")
public class DataSourceCallAutoConfiguration {

  @Bean
  DataSourceProxyBeanPostProcessor dataSourceProxyBeanPostProcessor(
          ExternalCallRecorder externalCallRecorder) {
    return new DataSourceProxyBeanPostProcessor(externalCallRecorder);
  }
}

package cbs.nova.starter;

import cbs.nova.starter.config.properties.TemporalTestProperties;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(TemporalTestProperties.class)
public class TemporalTestConfiguration {

  @Bean
  @Primary
  WorkflowServiceStubs workflowServiceStubs(TemporalTestProperties properties) {
    return WorkflowServiceStubs.newInstance(
            WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(properties.target())
                    .setEnableKeepAlive(true)
                    .build());
  }
}

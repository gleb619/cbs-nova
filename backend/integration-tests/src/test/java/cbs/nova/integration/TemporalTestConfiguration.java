package cbs.nova.integration;

import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TemporalTestConfiguration {

  @Bean
  @Primary
  WorkflowServiceStubs workflowServiceStubs(@Value("${temporal.target}") String target) {
    return WorkflowServiceStubs.newInstance(
        WorkflowServiceStubsOptions.newBuilder()
            .setTarget(target)
            .setEnableKeepAlive(true)
            .build());
  }
}

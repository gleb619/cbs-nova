package cbs.app.config;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

  @Value("${temporal.service-address:127.0.0.1:7233}")
  private String serviceAddress;

  @Bean(destroyMethod = "shutdown")
  public WorkflowServiceStubs workflowServiceStubs() {
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(serviceAddress).build());
  }

  @Bean
  public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
    return WorkflowClient.newInstance(stubs);
  }

  @Bean(destroyMethod = "shutdown")
  public WorkerFactory workerFactory(WorkflowClient client) {
    return WorkerFactory.newInstance(client);
  }
}

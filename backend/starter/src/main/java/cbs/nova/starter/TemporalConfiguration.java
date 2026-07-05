package cbs.nova.starter;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Temporal workflow infrastructure.
 *
 * <p>
 * Provides a default local {@link WorkflowServiceStubs} and {@link WorkflowClient} when the
 * application does not define its own beans.
 */
@AutoConfiguration
public class TemporalConfiguration {

  @Bean
  @ConditionalOnMissingBean
  WorkflowServiceStubs workflowServiceStubs() {
    return WorkflowServiceStubs.newLocalServiceStubs();
  }

  @Bean
  @ConditionalOnMissingBean
  WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs) {
    return WorkflowClient.newInstance(workflowServiceStubs);
  }
}

package cbs.nova.starter.config;

import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional configuration that exposes a {@link ScheduleClient} when a Temporal
 * {@link WorkflowServiceStubs} bean is present. The namespace matches the SDK default used by
 * {@link TemporalConfiguration#workflowClient} ("default" unless overridden).
 *
 * <p>
 * All schedule-aware beans downstream are guarded by
 * {@code @ConditionalOnBean(ScheduleClient.class)}, so when Temporal is not available the schedule
 * surface simply does not register.
 */
@Configuration
@ConditionalOnBean(WorkflowServiceStubs.class)
public class DslScheduleConfiguration {

  @Bean
  @ConditionalOnMissingBean(ScheduleClient.class)
  ScheduleClient scheduleClient(
          WorkflowServiceStubs workflowServiceStubs,
          @Value("${temporal.namespace:default}") String namespace) {
    ScheduleClientOptions options = ScheduleClientOptions.newBuilder()
            .setNamespace(namespace)
            .build();
    return ScheduleClient.newInstance(workflowServiceStubs, options);
  }
}

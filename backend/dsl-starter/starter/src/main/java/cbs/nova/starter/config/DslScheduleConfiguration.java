package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslScheduleProperties;
import cbs.nova.starter.service.DslScheduleService;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@AutoConfigureAfter(TemporalConfiguration.class)
@ConditionalOnBean(WorkflowServiceStubs.class)
@EnableConfigurationProperties(DslScheduleProperties.class)
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

  @Bean
  @ConditionalOnBean(ScheduleClient.class)
  DslScheduleService dslScheduleService(
          ScheduleClient scheduleClient,
          ObjectMapper objectMapper,
          DslScheduleProperties scheduleProperties) {
    return new DslScheduleService(scheduleClient, objectMapper, scheduleProperties.catchupWindow());
  }
}

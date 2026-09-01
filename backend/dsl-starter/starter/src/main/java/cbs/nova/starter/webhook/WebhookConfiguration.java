package cbs.nova.starter.webhook;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookConfiguration {

  @Bean
  WebhookDispatcher webhookDispatcher(WebhookProperties properties, ObjectMapper objectMapper,
          ThreadPoolTaskExecutor cbsNovaWebhookDeliveryExecutor) {
    return new WebhookDispatcher(properties, objectMapper, cbsNovaWebhookDeliveryExecutor);
  }

  @Bean(name = "cbsNovaWebhookDeliveryExecutor", destroyMethod = "shutdown")
  ThreadPoolTaskExecutor cbsNovaWebhookDeliveryExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("cbs-nova-webhook-");
    // Bounded queue with caller-runs policy: if the queue is full, the caller (the DSL run
    // completion path) runs the task itself. Because the dispatcher already offloaded matching
    // onto the delivery executor, caller-runs keeps the run path from blocking on saturation
    // while ensuring delivery still occurs.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}

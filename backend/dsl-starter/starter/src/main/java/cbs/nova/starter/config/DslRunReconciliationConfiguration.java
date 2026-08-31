package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.config.properties.DslRunReconciliationProperties;
import cbs.nova.starter.service.DslRunReconciliationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties(DslRunReconciliationProperties.class)
public class DslRunReconciliationConfiguration {

  @Bean(name = "cbsNovaDslRunReconciliationExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "cbsNovaDslRunReconciliationExecutor")
  ScheduledExecutorService cbsNovaDslRunReconciliationExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-reconciliation");
      t.setDaemon(true);
      return t;
    });
  }

  @Bean
  @ConditionalOnMissingBean(DslRunReconciliationService.class)
  @ConditionalOnProperty(prefix = "cbs.runs.reconciliation", name = "enabled", havingValue = "true")
  @ConditionalOnBean({DslRunRepository.class, WorkflowClient.class, MeterRegistry.class})
  DslRunReconciliationService dslRunReconciliationService(
          DslRunRepository runRepository,
          WorkflowClient workflowClient,
          MeterRegistry meterRegistry,
          DslRunReconciliationProperties properties,
          @Qualifier("cbsNovaDslRunReconciliationExecutor") ScheduledExecutorService executor) {
    return new DslRunReconciliationService(
            runRepository,
            workflowClient,
            meterRegistry,
            properties.getScanInterval(),
            properties.getGracePeriod(),
            properties.getBatchSize(),
            executor);
  }

  @Bean
  @ConditionalOnBean(DslRunReconciliationService.class)
  ApplicationRunner dslRunReconciliationServiceStarter(DslRunReconciliationService service) {
    return args -> service.start();
  }
}

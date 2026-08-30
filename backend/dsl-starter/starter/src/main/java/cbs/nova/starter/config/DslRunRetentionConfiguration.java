package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.starter.config.properties.DslRunRetentionProperties;
import cbs.nova.starter.service.DslRunRetentionPurger;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties(DslRunRetentionProperties.class)
public class DslRunRetentionConfiguration {

  @Bean(name = "cbsNovaDslRunRetentionExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "cbsNovaDslRunRetentionExecutor")
  ScheduledExecutorService cbsNovaDslRunRetentionExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-retention");
      t.setDaemon(true);
      return t;
    });
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean(DslRunRetentionPurger.class)
  @ConditionalOnBean({DslRunRepository.class, MeterRegistry.class})
  DslRunRetentionPurger dslRunRetentionPurger(
          DslRunRepository runRepository,
          MeterRegistry meterRegistry,
          DslRunRetentionProperties properties,
          @Qualifier("cbsNovaDslRunRetentionExecutor") ScheduledExecutorService executor,
          @Autowired(required = false) @Nullable TransactionExecutionRepository transactionExecutionRepository) {
    return new DslRunRetentionPurger(runRepository, meterRegistry,
            properties.getRetention(), properties.getPurgeInterval(),
            properties.getPurgeBatchSize(), executor, transactionExecutionRepository);
  }

  @Bean
  @ConditionalOnBean(DslRunRetentionPurger.class)
  ApplicationRunner dslRunRetentionPurgerStarter(DslRunRetentionPurger purger) {
    return args -> purger.start();
  }
}

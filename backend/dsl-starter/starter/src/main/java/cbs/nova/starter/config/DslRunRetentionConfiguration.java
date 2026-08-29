package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.config.properties.DslRunRetentionProperties;
import cbs.nova.starter.service.DslRunRetentionPurger;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Wires the scheduled {@code dsl_runs} retention purge. Mirrors the healthcheck-sweep wiring in
 * {@link TemporalConfiguration}: a dedicated single-thread scheduler runs the purge on a fixed
 * delay and is started once at application startup via an {@link ApplicationRunner}. When retention
 * is disabled (default), {@link DslRunRetentionPurger#start()} is a no-op and nothing is scheduled.
 */
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
          @Qualifier("cbsNovaDslRunRetentionExecutor") ScheduledExecutorService executor) {
    return new DslRunRetentionPurger(runRepository, meterRegistry,
            properties.getRetention(), properties.getPurgeInterval(),
            properties.getPurgeBatchSize(), executor);
  }

  @Bean
  @ConditionalOnBean(DslRunRetentionPurger.class)
  ApplicationRunner dslRunRetentionPurgerStarter(DslRunRetentionPurger purger) {
    return args -> purger.start();
  }
}

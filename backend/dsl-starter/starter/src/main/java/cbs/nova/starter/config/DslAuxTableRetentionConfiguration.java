package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsRetentionProperties;
import cbs.nova.starter.persistence.CompileDiagnosticCrudRepository;
import cbs.nova.starter.persistence.DslAuditCrudRepository;
import cbs.nova.starter.persistence.DslEventCrudRepository;
import cbs.nova.starter.service.DslAuxTableRetentionPurger;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the T836 aux-table retention purger.
 *
 * <p>
 * Registers a dedicated scheduled executor and the shared purger bean. The purger is a no-op until
 * at least one of {@code cbs.audit.retention}, {@code cbs.events.retention} or
 * {@code cbs.diagnostics.retention} is set to a positive duration.
 */
@Configuration
@EnableConfigurationProperties(CbsRetentionProperties.class)
public class DslAuxTableRetentionConfiguration {

  @Bean(name = "cbsNovaDslAuxTableRetentionExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "cbsNovaDslAuxTableRetentionExecutor")
  ScheduledExecutorService cbsNovaDslAuxTableRetentionExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-aux-table-retention");
      t.setDaemon(true);
      return t;
    });
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean(DslAuxTableRetentionPurger.class)
  @ConditionalOnBean({DslAuditCrudRepository.class, DslEventCrudRepository.class,
      CompileDiagnosticCrudRepository.class})
  DslAuxTableRetentionPurger dslAuxTableRetentionPurger(
          DslAuditCrudRepository auditRepository,
          DslEventCrudRepository eventRepository,
          CompileDiagnosticCrudRepository diagnosticRepository,
          CbsRetentionProperties properties,
          @Qualifier("cbsNovaDslAuxTableRetentionExecutor") ScheduledExecutorService executor,
          ObjectProvider<MeterRegistry> meterRegistryProvider) {
    return new DslAuxTableRetentionPurger(
            auditRepository,
            eventRepository,
            diagnosticRepository,
            meterRegistryProvider.getIfAvailable(),
            properties.getAudit(),
            properties.getEvents(),
            properties.getDiagnostics(),
            executor,
            Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean(DslAuxTableRetentionPurger.class)
  ApplicationRunner dslAuxTableRetentionPurgerStarter(DslAuxTableRetentionPurger purger) {
    return args -> purger.start();
  }
}

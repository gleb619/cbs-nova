package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslMaintenanceProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.maintenance.AuditRetentionMaintenanceTask;
import cbs.nova.starter.maintenance.DslMaintenanceService;
import cbs.nova.starter.maintenance.DslRunReconciliationMaintenanceTask;
import cbs.nova.starter.maintenance.DslRunRetentionMaintenanceTask;
import cbs.nova.starter.maintenance.MaintenanceTask;
import cbs.nova.starter.service.DslRunReconciliationService;
import cbs.nova.starter.service.DslRunRetentionPurger;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * T412 — folds the recurring purge/reconciliation tasks under one {@link DslMaintenanceService}
 * when {@code dsl.maintenance.unified.enabled=true}, and otherwise leaves the pre-existing per-job
 * schedulers alone.
 *
 * <h2>Rollout flag</h2>
 *
 * <ul>
 * <li>{@code dsl.maintenance.unified.enabled=false} (default) — each existing job keeps its own
 * {@code ApplicationRunner} self-schedule. Zero behaviour change, all pre-existing tests
 * green.</li>
 * <li>{@code dsl.maintenance.unified.enabled=true} — the per-job {@code ApplicationRunner} starters
 * are conditioned {@code @ConditionalOnProperty(..., matchIfMissing=true, havingValue="false")} so
 * they no-op themselves, and {@link DslMaintenanceService} runs each registered
 * {@link MaintenanceTask} on the unified schedule. The default schedule interval is the retention
 * purger's pre-existing cadence ({@code 1h}) so flipping the flag on causes no observable change in
 * purge frequency.</li>
 * </ul>
 *
 * <h2>Cadence preservation</h2>
 *
 * The unified schedule is intentionally a single interval rather than per-task intervals. Choosing
 * the retention purger's existing cadence as the default keeps the most-frequently-purged dataset
 * on its current rhythm; operators who need different cadences can keep
 * {@code unified.enabled=false} and continue using the per-job knobs.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DslMaintenanceProperties.class)
@AutoConfigureAfter({DslRunRetentionConfiguration.class, DslRunReconciliationConfiguration.class})
public class DslMaintenanceConfiguration {

  // ---------------------------------------------------------------------
  // Adapters — always registered when their underlying service exists.
  // They are no-ops until the unified driver invokes them. Pre-existing
  // per-job schedulers (DslRunRetentionConfiguration,
  // DslRunReconciliationConfiguration) call the same underlying methods,
  // so when unified is OFF the adapters sit unused.
  // ---------------------------------------------------------------------

  @Bean
  @ConditionalOnBean(DslRunRetentionPurger.class)
  DslRunRetentionMaintenanceTask dslRunRetentionMaintenanceTask(DslRunRetentionPurger purger) {
    return new DslRunRetentionMaintenanceTask(purger);
  }

  @Bean
  @ConditionalOnBean(DslRunReconciliationService.class)
  DslRunReconciliationMaintenanceTask dslRunReconciliationMaintenanceTask(
          DslRunReconciliationService service) {
    return new DslRunReconciliationMaintenanceTask(service);
  }

  /** Forward-compat no-op audit-retention stub. */
  @Bean
  AuditRetentionMaintenanceTask auditRetentionMaintenanceTask() {
    return new AuditRetentionMaintenanceTask();
  }

  // ---------------------------------------------------------------------
  // Unified driver + its executor — registered only when the master flag
  // is on.
  // ---------------------------------------------------------------------

  /**
   * Dedicated executor for the unified scheduler. Single-threaded (mirrors the pre-existing per-job
   * executors) and named so thread dumps remain legible.
   */
  @Bean(name = "cbsNovaDslMaintenanceExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "cbsNovaDslMaintenanceExecutor")
  @ConditionalOnProperty(prefix = "dsl.maintenance", name = "unified-enabled", havingValue = "true")
  ScheduledExecutorService cbsNovaDslMaintenanceExecutor() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-maintenance");
      t.setDaemon(true);
      return t;
    });
  }

  /**
   * Unified driver. Registered only when the master flag is on; absent otherwise (per-job
   * schedulers remain in charge).
   */
  @Bean(destroyMethod = "shutdown")
  @ConditionalOnProperty(prefix = "dsl.maintenance", name = "unified-enabled", havingValue = "true")
  DslMaintenanceService dslMaintenanceService(
          List<MaintenanceTask> tasks,
          DslMaintenanceProperties properties,
          @Qualifier("cbsNovaDslMaintenanceExecutor") ScheduledExecutorService executor,
          ObjectProvider<MeterRegistry> meterRegistryProvider) {
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    var perTask = properties.tasks();
    enabled.put(StarterConstants.RUN_RETENTION_TASK_NAME,
            perTask.runRetention().enabled());
    enabled.put(StarterConstants.ORPHANS_TASK_NAME,
            perTask.orphans().enabled());
    enabled.put(StarterConstants.AUDIT_RETENTION_TASK_NAME,
            perTask.auditRetention().enabled());
    // Deterministic ordering: sort by task name so logs and meter tag order
    // are stable regardless of bean-registration order.
    List<MaintenanceTask> sorted = tasks.stream()
            .sorted(Comparator.comparing(MaintenanceTask::name))
            .toList();
    return new DslMaintenanceService(sorted, enabled, properties.schedule(), executor,
            meterRegistryProvider.getIfAvailable());
  }

  /**
   * Starter for the unified scheduler. Registered only when the master flag is on, so {@code false}
   * (the default) leaves the per-job schedulers running untouched.
   */
  @Bean
  @ConditionalOnBean(DslMaintenanceService.class)
  ApplicationRunner dslMaintenanceServiceStarter(DslMaintenanceService service) {
    return args -> service.start();
  }

}

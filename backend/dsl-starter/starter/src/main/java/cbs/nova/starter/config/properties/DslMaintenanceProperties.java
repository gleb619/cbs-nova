package cbs.nova.starter.config.properties;

import jakarta.validation.Valid;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Unified configuration for the {@code dsl.maintenance.*} namespace.
 *
 * <p>
 * Folded in T412 from the pre-existing, independently-scheduled purge jobs (DslRunRetentionPurger,
 * DslRunReconciliationService). When {@link #unifiedEnabled} is {@code true} the unified service
 * drives those jobs; when {@code false} they keep their own per-job executors and schedules —
 * behaviour identical to today.
 *
 * <p>
 * The default {@link #schedule} interval is the retention purger's existing cadence ({@code 1h}).
 * Choosing a single cadence rather than per-task intervals keeps the unified driver deterministic
 * and easy to reason about; operators needing finer granularity can simply keep
 * {@code unified.enabled=false}.
 */
@Builder
@ConfigurationProperties(prefix = "dsl.maintenance")
@Validated
public record DslMaintenanceProperties(
        /**
         * Master rollout switch. When {@code false} (default), each individual purge/reconciliation
         * job continues to self-schedule as it did before T412; the unified service bean is not
         * registered. When {@code true}, the individual self-schedules are suppressed and the
         * unified service runs them in sequence on {@link #schedule}.
         */
        @DefaultValue("false") Boolean unifiedEnabled,
        /**
         * How often the unified service iterates over its {@code
         * MaintenanceTask} beans. Defaults to the retention purger's previous cadence ({@code 1h})
         * so flipping the flag on causes no observable change in purge frequency.
         */
        @DefaultValue("PT1H") Duration schedule,
        @Valid @DefaultValue Tasks tasks) {

  public DslMaintenanceProperties {
    unifiedEnabled = unifiedEnabled == null ? false : unifiedEnabled;
    schedule = schedule == null ? Duration.ofHours(1) : schedule;
    tasks = tasks == null ? new Tasks(null, null, null) : tasks;
  }

  /** Convenience for forward-compat key checks in tests/conditions. */
  public static final String PREFIX = "dsl.maintenance";

  @Builder
  public record Tasks(
          @Valid @DefaultValue Task runRetention,
          @Valid @DefaultValue Task orphans,
          @Valid @DefaultValue Task auditRetention) {

    public Tasks {
      runRetention = runRetention == null ? new Task(null) : runRetention;
      orphans = orphans == null ? new Task(null) : orphans;
      auditRetention = auditRetention == null ? new Task(null) : auditRetention;
    }
  }

  @Builder
  public record Task(@DefaultValue("true") Boolean enabled) {

    public Task {
      enabled = enabled == null ? true : enabled;
    }
  }
}

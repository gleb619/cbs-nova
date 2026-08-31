package cbs.nova.starter.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the recurring stuck {@code dsl_runs} reconciliation against Temporal.
 *
 * <p>
 * Follows the established opt-in pattern for behavior changes in this project (see the retention
 * purge and OIDC guard): the reconciliation is <em>disabled by default</em> and an operator
 * explicitly opts in by setting {@code cbs.runs.reconciliation.enabled=true}. Local DX and existing
 * deployments are therefore unaffected until the feature is enabled.
 */
@Data
@ConfigurationProperties(prefix = "cbs.runs.reconciliation")
public class DslRunReconciliationProperties {

  /**
   * Whether to schedule the reconciliation job. {@code false} (the default) disables it entirely —
   * no job is registered and stuck runs are left for the existing STALE sweep.
   */
  private boolean enabled = false;

  /** How often the reconciliation scans for stuck runs. */
  private Duration scanInterval = Duration.ofMinutes(5);

  /**
   * Only {@code RUNNING} rows whose {@code started_at} is older than this grace period are
   * inspected, giving in-flight lifecycle callbacks time to finish normally.
   */
  private Duration gracePeriod = Duration.ofMinutes(15);

  /** Max runs inspected per reconciliation pass. */
  private int batchSize = 200;
}

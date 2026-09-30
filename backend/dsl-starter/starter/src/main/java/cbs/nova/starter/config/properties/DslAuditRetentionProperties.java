package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Opt-in age-based retention purge for the append-only {@code dsl_audit} table (T747).
 *
 * <p>
 * Mirrors the {@link DslRunRetentionProperties} opt-in pattern: the purge is <em>disabled by
 * default</em> and an operator explicitly opts in by setting
 * {@code dsl.maintenance.audit-retention.enabled=true} together with a positive
 * {@code max-age-days}. The default append-only guarantee is preserved while the data is growing;
 * flipping the toggle on lets operators bound the table once they have a retention requirement.
 *
 * <p>
 * The purge is driven by the unified {@code dsl.maintenance} driver when
 * {@code dsl.maintenance.unified-enabled=true}, but is also wired into the existing
 * {@code audit-retention} task bean so it picks up scheduled execution either via the unified
 * service or directly when the unified driver is off. The single deletion path lives on
 * {@link cbs.nova.starter.persistence.DslAuditStore#purgeOlderThan(java.time.Instant, int)} and is
 * intentionally not reachable from any REST handler.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "dsl.maintenance.audit-retention")
public class DslAuditRetentionProperties {

  /**
   * Master switch. {@code false} (the default) makes the {@code audit-retention} maintenance task a
   * no-op — no SQL is issued and no rows are deleted. Operators must explicitly opt in for the
   * purge to run.
   */
  private boolean enabled = false;

  /**
   * Maximum age of an audit row before it becomes eligible for purging. A non-positive value keeps
   * the task disabled regardless of {@link #enabled} — same defensive posture as
   * {@link DslRunRetentionProperties#getRetention()}.
   */
  private Duration maxAge = Duration.ofDays(90);

  /**
   * Maximum rows removed per batched delete pass. Keeps each schema/row lock small; the same
   * rationale as {@link DslRunRetentionProperties#getPurgeBatchSize()}.
   */
  @Min(1)
  private int batchSize = 500;
}

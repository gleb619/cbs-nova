package cbs.nova.starter.config.properties;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Opt-in age-based retention purge for the append-only {@code dsl_webhook_deliveries} table (T814).
 *
 * <p>
 * Mirrors the {@link DslAuditRetentionProperties} opt-in pattern: the purge is <em>disabled by
 * default</em> and an operator explicitly opts in by setting
 * {@code dsl.maintenance.webhook-delivery-retention.enabled=true} together with a positive
 * {@code max-age}. The default append-only guarantee is preserved while the data is growing;
 * flipping the toggle on lets operators bound the table once they have a retention requirement.
 *
 * <p>
 * The purge is driven by the unified {@code dsl.maintenance} driver when
 * {@code dsl.maintenance.unified-enabled=true}, but is also wired into the existing
 * {@code webhook-delivery-retention} task bean so it picks up scheduled execution either via the
 * unified service or directly when the unified driver is off. The single deletion path lives on
 * {@link cbs.nova.starter.webhook.WebhookDeliveryRecordRepository#purgeOlderThan(java.time.Instant, int)}
 * and is intentionally not reachable from any REST handler.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "dsl.maintenance.webhook-delivery-retention")
public class DslWebhookDeliveryRetentionProperties {

  /**
   * Master switch. {@code false} (the default) makes the {@code webhook-delivery-retention}
   * maintenance task a no-op — no SQL is issued and no rows are deleted. Operators must explicitly
   * opt in for the purge to run.
   */
  private boolean enabled = false;

  /**
   * Maximum age of a delivery row before it becomes eligible for purging. A non-positive value
   * keeps the task disabled regardless of {@link #enabled} — same defensive posture as
   * {@link DslAuditRetentionProperties#getMaxAge()}.
   */
  private Duration maxAge = Duration.ofDays(30);

  /**
   * Maximum rows removed per batched delete pass. Keeps each schema/row lock small; the same
   * rationale as {@link DslAuditRetentionProperties#getBatchSize()}.
   */
  @Min(1)
  private int batchSize = 500;
}

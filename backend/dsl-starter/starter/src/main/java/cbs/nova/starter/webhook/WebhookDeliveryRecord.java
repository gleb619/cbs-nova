package cbs.nova.starter.webhook;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Row of the append-only {@code dsl_webhook_deliveries} table. Written exclusively by
 * {@code cbs.nova.starter.webhook.WebhookDeliveryRecordRepository}; there is intentionally no
 * update or delete path.
 */
@Table("dsl_webhook_deliveries")
public record WebhookDeliveryRecord(
        @Id @Column("id") @Nullable Long id,
        @Column("occurred_at") Instant occurredAt,
        @Column("subscription_id") String subscriptionId,
        @Column("event_type") String eventType,
        @Column("url") String url,
        @Column("status") String status,
        @Column("attempts") int attempts,
        @Column("last_error") @Nullable String lastError,
        @Column("duration_ms") @Nullable Long durationMs) {
}

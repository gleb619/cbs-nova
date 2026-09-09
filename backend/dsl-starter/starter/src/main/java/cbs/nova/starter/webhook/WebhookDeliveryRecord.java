package cbs.nova.starter.webhook;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of the append-only {@code dsl_webhook_deliveries} table. Written exclusively by
 * {@code cbs.nova.starter.webhook.WebhookDeliveryRecordRepository}; there is intentionally no
 * update or delete path.
 */
public record WebhookDeliveryRecord(
        @Nullable Long id,
        Instant occurredAt,
        String subscriptionId,
        String eventType,
        String url,
        String status,
        int attempts,
        @Nullable String lastError,
        @Nullable Long durationMs) {
}

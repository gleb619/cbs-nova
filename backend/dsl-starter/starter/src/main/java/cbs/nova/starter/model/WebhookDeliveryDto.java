package cbs.nova.starter.model;

import cbs.nova.starter.webhook.WebhookDeliveryRecord;
import org.jspecify.annotations.Nullable;


public record WebhookDeliveryDto(
        long id,
        String occurredAt,
        String subscriptionId,
        String eventType,
        String url,
        String status,
        int attempts,
        @Nullable String lastError,
        @Nullable Long durationMs) {

  public static WebhookDeliveryDto from(WebhookDeliveryRecord record) {
    return new WebhookDeliveryDto(
            record.id() != null ? record.id() : 0L,
            record.occurredAt().toString(),
            record.subscriptionId(),
            record.eventType(),
            record.url(),
            record.status(),
            record.attempts(),
            record.lastError(),
            record.durationMs());
  }
}

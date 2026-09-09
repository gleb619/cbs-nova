package cbs.nova.starter.webhook;

import java.util.List;

/**
 * Paged webhook delivery query result: the requested page plus the total number of matching rows.
 */
public record WebhookDeliverySearchResult(List<WebhookDeliveryRecord> items, long total) {
}

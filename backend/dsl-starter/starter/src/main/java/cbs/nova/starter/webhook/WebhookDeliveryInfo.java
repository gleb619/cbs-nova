package cbs.nova.starter.webhook;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record WebhookDeliveryInfo(
        String url,
        String definitionPattern,
        String lastStatus,
        int lastAttempts,
        @Nullable String lastError,
        @Nullable Instant lastDeliveredAt) {
}

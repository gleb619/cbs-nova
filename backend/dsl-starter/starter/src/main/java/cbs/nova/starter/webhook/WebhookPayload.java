package cbs.nova.starter.webhook;

import org.jspecify.annotations.Nullable;

public record WebhookPayload(
        String event,
        String runId,
        String definition,
        String status,
        String startedAt,
        String finishedAt,
        @Nullable String error) {
}

package cbs.nova.starter.webhook;

import java.util.Set;
import org.jspecify.annotations.Nullable;

public record WebhookSubscription(
        String definitionPattern,
        String url,
        @Nullable String secret,
        @Nullable Set<String> statuses) {
}

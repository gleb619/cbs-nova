package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of the append-only {@code dsl_notification_rule_firing} audit table. One row is written per
 * (matched rule, action) for every published domain event; synthetic {@code /test} runs do not
 * write rows.
 */
public record NotificationRuleFiringEntity(
        @Nullable Long id,
        long eventId,
        long ruleId,
        String ruleName,
        String sink,
        String outcome,
        @Nullable String detail,
        @Nullable Long durationMs,
        Instant createdAt) {
}

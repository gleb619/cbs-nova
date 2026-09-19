package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of {@code dsl_notification_rule}. The event filter is flattened into columns (mirroring how
 * the engine queries it: exact {@code event_type} + {@code enabled} in SQL, the remaining
 * dimensions in memory); the action list is stored as a JSON array in {@code actionsJson}.
 */
public record NotificationRuleEntity(
        @Nullable Long id,
        String name,
        boolean enabled,
        String eventType,
        @Nullable String aggregateType,
        @Nullable String aggregateIdPattern,
        @Nullable String definitionPattern,
        @Nullable String status,
        String actionsJson,
        int priority,
        String rateClass,
        Instant createdAt,
        Instant updatedAt) {
}

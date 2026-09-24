package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Row of {@code dsl_notification_rule}. The event filter is flattened into columns (mirroring how
 * the engine queries it: exact {@code event_type} + {@code enabled} in SQL, the remaining
 * dimensions in memory); the action list is stored as a JSON array in {@code actionsJson}.
 */
@Table("dsl_notification_rule")
public record NotificationRuleEntity(
        @Id @Column("id") @Nullable Long id,
        @Column("name") String name,
        @Column("enabled") boolean enabled,
        @Column("event_type") String eventType,
        @Column("aggregate_type") @Nullable String aggregateType,
        @Column("aggregate_id_pattern") @Nullable String aggregateIdPattern,
        @Column("definition_pattern") @Nullable String definitionPattern,
        @Column("status") @Nullable String status,
        @Column("actions") String actionsJson,
        @Column("priority") int priority,
        @Column("rate_class") String rateClass,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt) {
}

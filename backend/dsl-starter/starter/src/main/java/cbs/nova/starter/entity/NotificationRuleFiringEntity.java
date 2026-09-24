package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Row of the append-only {@code dsl_notification_rule_firing} audit table. One row is written per
 * (matched rule, action) for every published domain event; synthetic {@code /test} runs do not
 * write rows.
 */
@Table("dsl_notification_rule_firing")
public record NotificationRuleFiringEntity(
        @Id @Column("id") @Nullable Long id,
        @Column("event_id") long eventId,
        @Column("rule_id") long ruleId,
        @Column("rule_name") String ruleName,
        @Column("sink") String sink,
        @Column("outcome") String outcome,
        @Column("detail") @Nullable String detail,
        @Column("duration_ms") @Nullable Long durationMs,
        @Column("created_at") Instant createdAt) {
}

package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("dsl_events")
public record DslEventEntity(
        @Id @Column("id") @Nullable Long id,
        @Column("event_type") String eventType,
        @Column("aggregate_type") String aggregateType,
        @Column("aggregate_id") String aggregateId,
        @Column("correlation_id") @Nullable String correlationId,
        @Column("payload") String payloadJson,
        @Column("schema_version") int schemaVersion,
        @Column("created_at") Instant createdAt) {
}

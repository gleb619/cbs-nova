package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record DslEventEntity(
        @Nullable Long id,
        String eventType,
        String aggregateType,
        String aggregateId,
        @Nullable String correlationId,
        String payloadJson,
        int schemaVersion,
        Instant createdAt) {
}

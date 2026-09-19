package cbs.nova.starter.events.mq;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Canonical envelope broadcast to the message-queue topic. It wraps the same payload that is stored
 * in {@code dsl_events} with metadata consumers can use for filtering.
 */
public record DomainEventMqEnvelope(
        String eventType,
        String aggregateType,
        String aggregateId,
        @Nullable String correlationId,
        int schemaVersion,
        @Nullable Instant occurredAt,
        long dslEventRowId,
        JsonNode payload) {
}

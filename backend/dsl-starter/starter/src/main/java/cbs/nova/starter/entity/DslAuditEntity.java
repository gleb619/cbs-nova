package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of the append-only {@code dsl_audit} table. Written exclusively by
 * {@code cbs.nova.starter.persistence.DslAuditRepository}; there is intentionally no update or
 * delete path.
 */
public record DslAuditEntity(
        @Nullable Long id,
        Instant occurredAt,
        String actor,
        String action,
        String target,
        @Nullable String correlationId,
        String outcome,
        @Nullable String detailsJson) {
}

package cbs.nova.starter.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Domain model for one row of the append-only {@code dsl_audit} log. The service and handler layers
 * speak this record; persistence details stay behind {@code DslAuditEntity} and
 * {@code DslAuditStore}.
 */
public record DslAudit(
        @Nullable Long id,
        Instant occurredAt,
        String actor,
        String action,
        String target,
        @Nullable String correlationId,
        String outcome,
        @Nullable String detailsJson) {
}

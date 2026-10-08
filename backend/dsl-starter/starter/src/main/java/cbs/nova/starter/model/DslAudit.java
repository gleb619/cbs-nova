package cbs.nova.starter.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Domain model for one row of the append-only {@code dsl_audit} log. The service and handler layers
 * speak this record; persistence details stay behind {@code DslAuditEntity} and
 * {@code DslAuditStore}.
 *
 * <p>
 * T880: {@code prevHash} / {@code entryHash} carry the tamper-evident chain. The store populates
 * them on append; the verify endpoint recomputes the chain over every row and reports the first
 * divergence. Legacy rows (pre-V4) have both fields {@code null} and are skipped by verification.
 */
public record DslAudit(
        @Nullable Long id,
        Instant occurredAt,
        String actor,
        String action,
        String target,
        @Nullable String correlationId,
        String outcome,
        @Nullable String detailsJson,
        @Nullable String prevHash,
        @Nullable String entryHash) {
}

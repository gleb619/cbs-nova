package cbs.nova.starter.model;

import cbs.nova.starter.entity.DslAuditEntity;
import org.jspecify.annotations.Nullable;

/**
 * API view of a {@code dsl_audit} row, exposed by {@code GET /api/dsl/audit}. Timestamps are
 * rendered as ISO-8601 strings, matching the other execution DTOs.
 */
public record AuditDto(
        long id,
        String occurredAt,
        String actor,
        String action,
        String target,
        @Nullable String correlationId,
        String outcome,
        @Nullable String detailsJson) {

  public static AuditDto from(DslAuditEntity entity) {
    return new AuditDto(
            entity.id() != null ? entity.id() : 0L,
            entity.occurredAt().toString(),
            entity.actor(),
            entity.action(),
            entity.target(),
            entity.correlationId(),
            entity.outcome(),
            entity.detailsJson());
  }
}

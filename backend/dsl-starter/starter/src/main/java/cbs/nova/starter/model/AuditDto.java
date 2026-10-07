package cbs.nova.starter.model;

import cbs.nova.starter.security.JsonRedactor;
import org.jspecify.annotations.Nullable;

public record AuditDto(
        long id,
        String occurredAt,
        String actor,
        String action,
        String target,
        @Nullable String correlationId,
        String outcome,
        @Nullable String detailsJson) {

  public static AuditDto from(DslAudit audit) {
    return from(audit, JsonRedactor.disabled());
  }

  public static AuditDto from(DslAudit audit, JsonRedactor redactor) {
    return new AuditDto(
            audit.id() != null ? audit.id() : 0L,
            audit.occurredAt().toString(),
            audit.actor(),
            audit.action(),
            audit.target(),
            audit.correlationId(),
            audit.outcome(),
            redactor.redact(audit.detailsJson()));
  }
}

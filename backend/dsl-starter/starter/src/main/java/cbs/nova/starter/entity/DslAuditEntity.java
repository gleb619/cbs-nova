package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;


// TODO: create at `model` package a `DslAudit` model, and redo `DslAuditEntity` to a spring data
// entity with `org.springframework.data.relational.core.mapping.Table`
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

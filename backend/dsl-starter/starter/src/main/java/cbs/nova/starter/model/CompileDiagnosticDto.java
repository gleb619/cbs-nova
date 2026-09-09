package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * API view of a {@code dsl_compile_diagnostics} row, exposed by {@code GET /api/dsl/diagnostics}.
 * Timestamps are rendered as ISO-8601 strings, matching the other execution DTOs.
 */
public record CompileDiagnosticDto(
        long id,
        String occurredAt,
        String source,
        String definition,
        @Nullable String file,
        @Nullable Long line,
        @Nullable Long column,
        String severity,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String code,
        String message) {

  public static CompileDiagnosticDto from(CompileDiagnosticRecord record) {
    return new CompileDiagnosticDto(
            record.id() != null ? record.id() : 0L,
            record.occurredAt().toString(),
            record.source(),
            record.definition(),
            record.file(),
            record.line(),
            record.column(),
            record.severity(),
            record.code(),
            record.message());
  }
}

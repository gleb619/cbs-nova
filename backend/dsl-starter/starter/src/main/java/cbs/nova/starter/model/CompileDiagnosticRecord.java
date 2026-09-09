package cbs.nova.starter.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of the append-only {@code dsl_compile_diagnostics} table. Written exclusively by
 * {@code cbs.nova.starter.persistence.CompileDiagnosticRecordRepository}; there is intentionally no
 * update or delete path.
 */
public record CompileDiagnosticRecord(
        @Nullable Long id,
        Instant occurredAt,
        String source,
        String definition,
        @Nullable String file,
        @Nullable Long line,
        @Nullable Long column,
        String severity,
        @Nullable String code,
        String message) {
}

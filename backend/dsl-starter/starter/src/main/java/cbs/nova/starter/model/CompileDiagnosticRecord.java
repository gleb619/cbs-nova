package cbs.nova.starter.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;


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

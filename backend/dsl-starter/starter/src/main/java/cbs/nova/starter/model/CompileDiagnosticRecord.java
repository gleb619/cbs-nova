package cbs.nova.starter.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("dsl_compile_diagnostics")
public record CompileDiagnosticRecord(
        @Id @Column("id") @Nullable Long id,
        @Column("occurred_at") Instant occurredAt,
        @Column("source") String source,
        @Column("definition") String definition,
        @Column("file") @Nullable String file,
        @Column("line") @Nullable Long line,
        @Column("col_number") @Nullable Long column,
        @Column("severity") String severity,
        @Column("code") @Nullable String code,
        @Column("message") String message) {
}

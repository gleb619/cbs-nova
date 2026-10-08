package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC entity for the append-only {@code dsl_audit} table. Immutable by design: the
 * audit log is append-only by convention, so no update path exists anywhere in the codebase.
 *
 * <p>
 * T880 adds the tamper-evident hash chain: {@code prevHash} points to the previous row's
 * {@code entryHash} and {@code entryHash} is {@code sha256(prevHash || canonical(row fields))}.
 * Legacy rows (pre-V4) leave both columns {@code NULL}; {@code DslAuditStore#verifyChain} skips
 * them and resumes verification at the first post-migration row.
 */
@Table("dsl_audit")
public record DslAuditEntity(
        @Nullable @Id @Column("id") Long id,
        @Column("occurred_at") Instant occurredAt,
        @Column("actor") String actor,
        @Column("action") String action,
        @Column("target") String target,
        @Nullable @Column("correlation_id") String correlationId,
        @Column("outcome") String outcome,
        @Nullable @Column("details_json") String detailsJson,
        @Nullable @Column("prev_hash") String prevHash,
        @Nullable @Column("entry_hash") String entryHash) {
}

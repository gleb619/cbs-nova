package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Row of {@code dsl_definition_snapshots} (T875): the canonical source snapshot a definition was
 * published/reloaded with, keyed by {@code (definition_name, content_hash)} so an unchanged hash
 * never yields a duplicate row.
 */
@Table("dsl_definition_snapshots")
public record DslDefinitionSnapshotEntity(
        @Id @Column("id") @Nullable Long id,
        @Column("definition_name") String definitionName,
        @Column("definition_version") @Nullable String definitionVersion,
        @Column("content_hash") String contentHash,
        @Column("source_text") String sourceText,
        @Column("captured_at") Instant capturedAt,
        @Column("captured_by") String capturedBy) {
}

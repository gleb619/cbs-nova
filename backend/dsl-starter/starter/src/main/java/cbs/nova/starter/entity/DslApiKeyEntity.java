package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("dsl_api_keys")
public record DslApiKeyEntity(
        @Id @Column("id") @Nullable Long id,
        @Column("label") String label,
        @Column("key_hash") String keyHash,
        @Column("key_prefix") String keyPrefix,
        @Column("created_at") Instant createdAt,
        @Column("revoked_at") @Nullable Instant revokedAt,
        @Column("last_used_at") @Nullable Instant lastUsedAt) {
}

package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Row of the {@code dsl_api_keys} table (T410). One row per issued key. {@code keyHash} is the
 * SHA-256 hex digest of the plaintext — the plaintext itself is never persisted and never
 * reconstructable from the row. {@code keyPrefix} is the first eight characters of the plaintext
 * (base64url) and is shown in the admin list endpoint purely for human identification.
 *
 * <p>
 * A row is "active" when {@code revokedAt == null}. The unique index on {@code keyHash} is the only
 * equality check used by
 * {@code cbs.nova.starter.persistence.JdbcApiKeyRepository#findActiveByHash}.
 */
public record DslApiKeyEntity(
        @Nullable Long id,
        String label,
        String keyHash,
        String keyPrefix,
        Instant createdAt,
        @Nullable Instant revokedAt,
        @Nullable Instant lastUsedAt) {
}

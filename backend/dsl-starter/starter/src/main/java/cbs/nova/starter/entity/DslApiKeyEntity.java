package cbs.nova.starter.entity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;


public record DslApiKeyEntity(
        @Nullable Long id,
        String label,
        String keyHash,
        String keyPrefix,
        Instant createdAt,
        @Nullable Instant revokedAt,
        @Nullable Instant lastUsedAt) {
}

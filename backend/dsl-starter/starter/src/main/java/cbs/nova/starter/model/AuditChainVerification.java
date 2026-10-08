package cbs.nova.starter.model;

import org.jspecify.annotations.Nullable;

/**
 * Result of {@code GET /api/dsl/audit/verify} (T880). {@code ok} is {@code true} only when the
 * entire chain — every post-migration {@code dsl_audit} row — recomputed its {@code entry_hash}
 * correctly and every row's {@code prev_hash} matched the previous row's {@code entry_hash}.
 * {@code checked} counts the post-migration rows the walker actually examined.
 * {@code firstDivergenceId} is the {@code dsl_audit.id} of the first row whose hash did not match
 * (or whose {@code prev_hash} broke the chain); {@code firstDivergenceAt} is that row's
 * {@code occurred_at} (when available). Both are {@code null} when {@code ok} is true.
 */
public record AuditChainVerification(
        boolean ok,
        long checked,
        @Nullable Long firstDivergenceId,
        @Nullable String firstDivergenceAt,
        @Nullable String reason) {
}

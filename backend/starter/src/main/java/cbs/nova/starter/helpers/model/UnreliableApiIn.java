package cbs.nova.starter.helpers.model;

import org.jspecify.annotations.Nullable;

public record UnreliableApiIn(
        String operationId,
        int failCount,
        boolean jitter,
        @Nullable String reason) {
}

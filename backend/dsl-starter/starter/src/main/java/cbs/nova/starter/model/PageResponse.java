package cbs.nova.starter.model;

import java.util.List;

/**
 * Shared list envelope for all DSL paged endpoints.
 *
 * <p>Fields mirror the clamped offset/limit actually applied by the handler,
 * so callers can reliably compute "has more" / next-page cursors.
 */
public record PageResponse<T>(List<T> items, long total, int offset, int limit) {
}

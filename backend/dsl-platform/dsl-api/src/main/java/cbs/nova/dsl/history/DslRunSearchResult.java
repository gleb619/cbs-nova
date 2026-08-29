package cbs.nova.dsl.history;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Paged result of a {@link DslRunRepository#search} query.
 *
 * <p>
 * Carries both the page of items and the total number of rows matching the same filters, so the
 * caller can build a stable paginated response without a separate count call.
 */
public record DslRunSearchResult(@NonNull List<DslRun> items, int total) {
}

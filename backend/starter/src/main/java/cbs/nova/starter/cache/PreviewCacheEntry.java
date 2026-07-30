package cbs.nova.starter.cache;

import cbs.nova.dsl.PreviewReport;
import org.jspecify.annotations.NonNull;

public record PreviewCacheEntry(
        @NonNull PreviewReport report,
        long timestamp,
        long ttlMs) {

}

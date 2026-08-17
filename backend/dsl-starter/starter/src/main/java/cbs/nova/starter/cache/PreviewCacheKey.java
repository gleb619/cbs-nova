package cbs.nova.starter.cache;

import org.jspecify.annotations.NonNull;

public record PreviewCacheKey(
        @NonNull String processName,
        @NonNull String dslDescriptorHash,
        @NonNull String inputHash) {

}

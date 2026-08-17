package cbs.nova.starter.core.recorder;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record ExternalCall(
        @NonNull String type,
        @NonNull String target,
        @NonNull String operation,
        long timestamp,
        @NonNull Map<String, Object> metadata) {
}

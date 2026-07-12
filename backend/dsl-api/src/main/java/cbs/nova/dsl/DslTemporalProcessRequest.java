package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record DslTemporalProcessRequest<T>(@NonNull String runId, @Nullable T payload) {
}

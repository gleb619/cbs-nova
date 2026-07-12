package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record DslTemporalProcessRequest(@NonNull String runId, @Nullable Object payload) {
}

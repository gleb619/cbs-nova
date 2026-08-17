package cbs.nova.dsl.transaction;

import cbs.nova.dsl.process.DslTemporalProcessRequest;
import io.avaje.jsonb.Json;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Json
public record DslTemporalTransactionRequest<T>(@NonNull String runId, @Nullable T payload) {
}

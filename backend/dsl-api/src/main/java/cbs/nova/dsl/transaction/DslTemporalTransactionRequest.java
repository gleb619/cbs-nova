package cbs.nova.dsl.transaction;

import cbs.nova.dsl.process.DslTemporalProcessRequest;
import io.avaje.jsonb.Json;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Typed request envelope passed to generated Temporal transaction activities.
 *
 * <p>
 * Mirrors {@link DslTemporalProcessRequest} so the transaction API also carries the DSL run id
 * together with the typed payload.
 * </p>
 */
@Json
public record DslTemporalTransactionRequest<T>(@NonNull String runId, @Nullable T payload) {
}

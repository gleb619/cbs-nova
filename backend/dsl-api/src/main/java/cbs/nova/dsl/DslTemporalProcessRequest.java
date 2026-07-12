package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Universal request payload for generated Temporal DSL process workflows.
 *
 * <p>
 * Carries the DSL run identifier together with the typed process input so the generated workflow
 * implementation can build a {@link Context} without relying on reflection.
 */
public record DslTemporalProcessRequest(@NonNull String runId, @Nullable Object payload) {
}

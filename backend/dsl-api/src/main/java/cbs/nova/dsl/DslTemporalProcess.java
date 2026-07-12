package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Common contract implemented by every generated DSL process workflow interface.
 *
 * <p>
 * The generated sub-interface redeclares {@link #execute(DslTemporalProcessRequest)} with
 * {@code @WorkflowMethod}. Keeping this base interface in {@code dsl-api} (which has no Temporal
 * dependency) lets callers such as {@link TemporalProcessLauncher} invoke workflows by their
 * logical contract instead of reflection.
 */
public interface DslTemporalProcess {

  /**
   * Executes the DSL process for the given request.
   *
   * @param request
   *          the request carrying the DSL run id and typed input payload
   * @return the process output, or {@code null} when the process returns an empty result
   */
  @Nullable
  Object execute(@NonNull DslTemporalProcessRequest request);
}

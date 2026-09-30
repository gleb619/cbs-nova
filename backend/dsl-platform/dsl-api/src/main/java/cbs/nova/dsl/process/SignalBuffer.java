package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Mutable buffer of signal payloads for a single process execution.
 *
 * <p>
 * Two implementations exist:
 * <ul>
 * <li>the generated Temporal workflow buffer (real signals, wall-clock {@code Workflow.await})
 * —</li>
 * <li>{@link PreviewSignalBuffer}, a deterministic seeded buffer used by preview / hierarchy /
 * explain.</li>
 * </ul>
 *
 * <p>
 * The buffer is installed on the execution context under
 * {@link cbs.nova.dsl.config.Constants#SIGNAL_AWAITER_METADATA_KEY}; {@link ProcessContext} only
 * ever sees the {@link SignalAwaiter} view of it.
 */
public interface SignalBuffer extends SignalAwaiter {

  /**
   * Records a delivered signal. Later deliveries of the same name overwrite the payload, matching
   * Temporal signal semantics.
   */
  void receive(@NonNull String name, @NonNull Object payload);

  /**
   * Current payload state keyed by signal name. Used by diagnostics / state queries.
   */
  @NonNull
  Map<String, Object> snapshot();
}

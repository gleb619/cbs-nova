package cbs.nova.starter.vhs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Abstraction for writing VHS tape events.
 *
 * <p>
 * Implementations are responsible for durably persisting the events of a single run. The recorder
 * calls {@link #start} when a run begins, {@link #append} for each event, and {@link #close} once
 * when the run completes (or aborts).
 */
public interface VhsTapeSink {

  /**
   * Start a new tape for the given run.
   *
   * @param runId
   *          the run identifier
   * @param route
   *          the route or entry point that produced the run
   * @param correlationId
   *          correlation id propagated from the execution context, may be null
   */
  void start(@NonNull String runId, @NonNull String route, @Nullable String correlationId);

  /**
   * Append an event for the given run.
   *
   * @param runId
   *          the run identifier
   * @param event
   *          the tape event to append
   */
  void append(@NonNull String runId, @NonNull TapeEvent event);

  /**
   * Close the tape for the given run, flushing any buffered content and writing a trailer line.
   *
   * @param runId
   *          the run identifier
   * @return the number of events persisted in the tape (excluding the header and trailer)
   */
  long close(@NonNull String runId);
}

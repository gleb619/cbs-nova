package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-run execution-trace collector.
 *
 * <p>
 * One instance corresponds to exactly one run: it is created by the owning pipe stage at run entry
 * (see {@code ExecutionTraceStage}) and dropped when the run ends, so its state can never leak
 * across runs. State lives in instance fields — there is no runId-keyed map and no shared
 * singleton. The same instance-scoping discipline keeps the collector safe on Temporal worker nodes
 * where runs execute outside the requesting thread.
 */
public final class ExecutionTraceCollector {

  private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
  private boolean active;

  public ExecutionTraceCollector() {
  }

  /** Marks the run as started. Entries are only collected while active. */
  public void start() {
    synchronized (entries) {
      active = true;
    }
  }

  /**
   * Appends an entry to the trace. Ignored when the collector has not been started or has already
   * been stopped, which prevents a stale collector reference from seeing new-run data.
   */
  public void add(@NonNull String entry) {
    synchronized (entries) {
      if (active) {
        entries.add(entry);
      }
    }
  }

  /** Returns an immutable copy of the entries collected so far. */
  public @NonNull List<String> snapshot() {
    synchronized (entries) {
      return List.copyOf(entries);
    }
  }

  /** Marks the run as finished and discards any collected entries. */
  public void stop() {
    synchronized (entries) {
      active = false;
      entries.clear();
    }
  }
}

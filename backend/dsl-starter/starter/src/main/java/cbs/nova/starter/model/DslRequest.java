package cbs.nova.starter.model;

import cbs.nova.dsl.process.SeededSignal;

import java.util.List;
import java.util.Map;

/**
 * Runtime request body shared by the preview / run / explain / hierarchy endpoints.
 *
 * @param body
 *          process input payload
 * @param metadata
 *          arbitrary context metadata
 * @param signals
 *          optional preview signal timeline (preview / hierarchy / explain). Each entry seeds one
 *          signal payload and the await-point index it becomes visible at. {@code null} or empty
 *          preserves the pre-T736 behavior of signals being inert in preview. Ignored by
 *          {@code run} mode, where the generated Temporal buffer owns signal delivery.
 */
public record DslRequest(Object body, Map<String, Object> metadata,
        List<SeededSignal> signals) {

  /** Backward-compatible constructor for callers that do not seed signals. */
  public DslRequest(Object body, Map<String, Object> metadata) {
    this(body, metadata, null);
  }
}

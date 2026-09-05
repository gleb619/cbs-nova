package cbs.nova.starter.helper.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Output for the built-in {@code otel} helper.
 *
 * <p>
 * The {@code result} field carries the per-mode return value:
 * <ul>
 * <li>{@code "span"} — the W3C traceparent string identifying the started span.</li>
 * <li>{@code "endSpan"} — {@code true} on successful end (no meaningful payload).</li>
 * <li>{@code "addEvent"} — {@code true} on successful event attachment.</li>
 * <li>{@code "setBaggage"} — {@code true} on successful local-baggage write.</li>
 * <li>{@code "getBaggage"} — the stored baggage value as a string.</li>
 * <li>{@code "injectContext"} — the carrier map with W3C headers injected.</li>
 * <li>{@code "extractContext"} — the extracted span-id hex string, or empty string when no valid
 * traceparent was present.</li>
 * </ul>
 */
public record OtelOut(@Nullable Object result) {

  /**
   * Convenience factory for the no-payload success modes.
   */
  public static OtelOut ok() {
    return new OtelOut(Boolean.TRUE);
  }

  /**
   * Convenience factory for the success modes that return a string.
   */
  public static OtelOut ofString(@Nullable String value) {
    return new OtelOut(value);
  }

  /**
   * Convenience factory for the success modes that return a map.
   */
  public static OtelOut ofMap(@Nullable Map<String, String> value) {
    return new OtelOut(value);
  }
}

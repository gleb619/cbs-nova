package cbs.nova.starter.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Typed dry-run log event captured by {@link DryRunLogbackAppender}.
 *
 * @param level
 *          the SLF4J/Logback level name (e.g. "INFO")
 * @param message
 *          the formatted log message
 * @param timestampMillis
 *          the event timestamp from Logback, in milliseconds since epoch
 * @param mdc
 *          the mapped diagnostic context at the time of the event
 * @param runId
 *          the dry-run runId that was active when the event was emitted
 */
public record DryRunLogEvent(
        @NonNull String level,
        @NonNull String message,
        long timestampMillis,
        @NonNull Map<String, String> mdc,
        @Nullable String runId) {

  public DryRunLogEvent {
    mdc = Map.copyOf(mdc);
  }
}

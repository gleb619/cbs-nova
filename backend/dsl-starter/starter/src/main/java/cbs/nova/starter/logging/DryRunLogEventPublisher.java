package cbs.nova.starter.logging;

import org.jspecify.annotations.NonNull;

/**
 * Push channel for dry-run log events as they are appended. The {@link DryRunLogBuffer} stays the
 * source of truth for history/replay; a publisher only mirrors live rows to interested consumers
 * (e.g. the dry-run log SSE stream).
 */
@FunctionalInterface
public interface DryRunLogEventPublisher {

  void publish(@NonNull String runId, @NonNull DryRunLogEvent event);
}

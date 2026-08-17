package cbs.nova.starter.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

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

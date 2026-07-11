package cbs.nova.dsl.config;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.SimpleContext;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public final class ContextFactory {

  public @NonNull String generateRunId() {
    return "run-" + UUID.randomUUID();
  }

  public <U> @NonNull SimpleContext<U> of(@NonNull U body, @NonNull ExecutionMode mode) {
    return new SimpleContext<>(body, Map.of(), mode, generateRunId());
  }

  public <U> @NonNull SimpleContext<U> of(
          @NonNull U body, @NonNull ExecutionMode mode, @NonNull String runId) {
    return new SimpleContext<>(body, Map.of(), mode, runId);
  }

  public <U> @NonNull SimpleContext<U> of(
          @NonNull U body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return new SimpleContext<>(body, metadata, mode, runId);
  }
}

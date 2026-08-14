package cbs.nova.dsl.config;

import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.transaction.TransactionRouting;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public final class ContextFactory {

  public @NonNull String generateRunId() {
    return "run-" + UUID.randomUUID();
  }

  public <U> @NonNull SimpleContext<U> of(@NonNull U body, @NonNull ExecutionMode mode) {
    return new SimpleContext<>(body, Map.of(), mode, generateRunId(), TransactionRouting.LOCAL,
            null, null, null);
  }

  public <U> @NonNull SimpleContext<U> of(
          @NonNull U body, @NonNull ExecutionMode mode, @NonNull String runId) {
    return new SimpleContext<>(body, Map.of(), mode, runId, TransactionRouting.LOCAL,
            null, null, null);
  }

  public <U> @NonNull SimpleContext<U> of(
          @NonNull U body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return new SimpleContext<>(body, metadata, mode, runId, TransactionRouting.LOCAL,
            null, null, null);
  }

  public <U> @NonNull SimpleContext<U> of(
          @NonNull U body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            null, null, null);
  }

  public <U> @NonNull SimpleContext<U> of(
          @NonNull U body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting,
          @Nullable ExecutionListener executionListener) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, null, null);
  }

  public <U> @NonNull SimpleContext<U> of(
          @NonNull U body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting,
          @Nullable ExecutionListener executionListener,
          @Nullable DslSaga saga) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga, null);
  }
}

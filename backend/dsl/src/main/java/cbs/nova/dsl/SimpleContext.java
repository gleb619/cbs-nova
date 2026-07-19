package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable context implementation. The stored body is returned as-is, so {@link MapInput} bodies
 * remain {@link MapInput} for parameter-based DSL definitions.
 */
public final class SimpleContext<T> implements Context<T> {

  private final Object body;
  private final Map<String, Object> metadata;
  private final ExecutionMode mode;
  private final String runId;
  private final TransactionRouting transactionRouting;
  private final ExecutionListener executionListener;
  private final DslSaga saga;

  public SimpleContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    this(body, metadata, mode, runId, TransactionRouting.LOCAL, null, null);
  }

  public SimpleContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting) {
    this(body, metadata, mode, runId, transactionRouting, null, null);
  }

  public SimpleContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting,
          @Nullable ExecutionListener executionListener) {
    this(body, metadata, mode, runId, transactionRouting, executionListener, null);
  }

  public SimpleContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting,
          @Nullable ExecutionListener executionListener,
          @Nullable DslSaga saga) {
    this.body = body;
    this.metadata = metadata;
    this.mode = mode;
    this.runId = runId;
    this.transactionRouting = transactionRouting;
    this.executionListener = executionListener;
    this.saga = saga;
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NonNull T body() {
    return (T) body;
  }

  @Override
  public @NonNull Map<String, Object> metadata() {
    return metadata;
  }

  @Override
  public @NonNull ExecutionMode mode() {
    return mode;
  }

  @Override
  public @NonNull String runId() {
    return runId;
  }

  @Override
  public @NonNull TransactionRouting transactionRouting() {
    return transactionRouting;
  }

  @Override
  public @Nullable ExecutionListener executionListener() {
    return executionListener;
  }

  @Override
  public @Nullable DslSaga saga() {
    return saga;
  }

  @Override
  public <U> @NonNull Context<U> withBody(@NonNull U newBody) {
    return new SimpleContext<>(newBody, metadata, mode, runId, transactionRouting,
            executionListener, saga);
  }

  @Override
  public @NonNull Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new LinkedHashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode, runId, transactionRouting,
            executionListener, saga);
  }

  @Override
  public @NonNull Context<T> withTransactionRouting(@NonNull TransactionRouting routing) {
    return new SimpleContext<>(body, metadata, mode, runId, routing, executionListener, saga);
  }

  @Override
  public @NonNull Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting, listener, saga);
  }

  @Override
  public @NonNull Context<T> withSaga(@Nullable DslSaga saga) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga);
  }
}

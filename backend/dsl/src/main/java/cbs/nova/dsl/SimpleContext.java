package cbs.nova.dsl;

import cbs.nova.dsl.config.DslConfig;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable context implementation. The stored body is returned as-is, so {@link MapInput} bodies
 * remain {@link MapInput} for parameter-based DSL definitions.
 */
@Builder
@RequiredArgsConstructor
public final class SimpleContext<T> implements Context<T> {

  private final Object body;
  private final Map<String, Object> metadata;
  private final ExecutionMode mode;
  private final String runId;
  private final TransactionRouting transactionRouting;
  private final ExecutionListener executionListener;
  private final DslSaga saga;
  private final ExecutionTraceCollector executionTraceCollector;

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
  public @Nullable ExecutionTraceCollector executionTraceCollector() {
    return executionTraceCollector;
  }

  @Override
  public @NonNull Object eval(@NonNull String expression) {
    return eval(expression, Map.of());
  }

  @Override
  public @NonNull Object eval(@NonNull String expression,
          @NonNull Map<String, Object> variables) {
    Map<String, Object> merged = new LinkedHashMap<>();
    merged.putAll(metadata);
    if (body instanceof MapInput mapInput) {
      merged.putAll(mapInput.asMap());
    } else if (body instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      merged.putAll(typed);
    }
    merged.putAll(variables);
    return DslConfig.dslConfig().expressionEvaluator().get().evaluate(expression, merged);
  }

  @Override
  public <U> @NonNull Context<U> withBody(@NonNull U newBody) {
    return new SimpleContext<>(newBody, metadata, mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector);
  }

  @Override
  public @NonNull Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new LinkedHashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector);
  }

  @Override
  public @NonNull Context<T> withTransactionRouting(@NonNull TransactionRouting routing) {
    return new SimpleContext<>(body, metadata, mode, runId, routing, executionListener, saga,
            executionTraceCollector);
  }

  @Override
  public @NonNull Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting, listener, saga,
            executionTraceCollector);
  }

  @Override
  public @NonNull Context<T> withSaga(@Nullable DslSaga saga) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector);
  }

  @Override
  public @NonNull Context<T> withExecutionTraceCollector(
          @Nullable ExecutionTraceCollector executionTraceCollector) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector);
  }
}

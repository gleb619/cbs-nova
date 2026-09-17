package cbs.nova.dsl.model;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.BeanResolver;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.JsonValue;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.json.AvajeJsonValue;
import cbs.nova.dsl.listener.ExecutionListener;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.NoopSaga;
import cbs.nova.dsl.helper.NoopHelperInterceptor;
import cbs.nova.dsl.listener.NoopExecutionListener;
import cbs.nova.dsl.listener.NoopExecutionTraceCollector;
import cbs.nova.dsl.transaction.TransactionRouting;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

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
  private final HelperInterceptor helperInterceptor;
  private final BeanResolver beanResolver;

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
  public @NonNull ExecutionListener executionListener() {
    return executionListener != null
            ? executionListener
            : NoopExecutionListener.INSTANCE;
  }

  @Override
  public @NonNull DslSaga saga() {
    return saga != null ? saga : NoopSaga.INSTANCE;
  }

  @Override
  public @NonNull ExecutionTraceCollector executionTraceCollector() {
    return executionTraceCollector != null
            ? executionTraceCollector
            : NoopExecutionTraceCollector.INSTANCE;
  }

  @Override
  public @NonNull HelperInterceptor helperInterceptor() {
    return helperInterceptor != null
            ? helperInterceptor
            : NoopHelperInterceptor.INSTANCE;
  }

  @Override
  public @NonNull BeanResolver beanResolver() {
    // TODO: we always must work with a `DslConfig.dslConfig().beanResolver()` as a field, so
    // beanResolver cant be null
    @Deprecated
    BeanResolver resolver = beanResolver != null
            ? beanResolver
            : DslConfig.dslConfig().beanResolver().get();
    if (resolver == null) {
      throw new UnsupportedOperationException("BeanResolver is not configured");
    }
    return resolver;
  }

  @Override
  public @NonNull JsonValue json() {
    return toJsonValue(body);
  }

  @Override
  public @NonNull JsonValue json(@Nullable Object value) {
    return toJsonValue(value);
  }

  private static @NonNull JsonValue toJsonValue(@Nullable Object value) {
    if (value == null) {
      return AvajeJsonValue.missing();
    }
    if (value instanceof JsonValue jsonValue) {
      return jsonValue;
    }
    if (value instanceof String string) {
      if (string.isBlank()) {
        return AvajeJsonValue.missing();
      }
      try {
        return AvajeJsonValue.parse(string);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
      }
    }
    return AvajeJsonValue.of(value);
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
    merged.put("body", body);
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
            executionListener, saga, executionTraceCollector, helperInterceptor, beanResolver);
  }

  @Override
  public @NonNull Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new LinkedHashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector, helperInterceptor, beanResolver);
  }

  @Override
  public @NonNull Context<T> withTransactionRouting(@NonNull TransactionRouting routing) {
    return new SimpleContext<>(body, metadata, mode, runId, routing, executionListener, saga,
            executionTraceCollector, helperInterceptor, beanResolver);
  }

  @Override
  public @NonNull Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting, listener, saga,
            executionTraceCollector, helperInterceptor, beanResolver);
  }

  @Override
  public @NonNull Context<T> withSaga(@Nullable DslSaga saga) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector, helperInterceptor, beanResolver);
  }

  @Override
  public @NonNull Context<T> withExecutionTraceCollector(
          @Nullable ExecutionTraceCollector executionTraceCollector) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector, helperInterceptor, beanResolver);
  }

  @Override
  public @NonNull Context<T> withHelperInterceptor(@Nullable HelperInterceptor interceptor) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector, interceptor, beanResolver);
  }

  @Override
  public @NonNull Context<T> withBeanResolver(@Nullable BeanResolver beanResolver) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            executionListener, saga, executionTraceCollector, helperInterceptor, beanResolver);
  }
}

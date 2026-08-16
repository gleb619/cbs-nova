package cbs.nova.dsl;

import cbs.nova.dsl.transaction.TransactionRouting;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface Context<T> {

  @NonNull
  T body();

  @NonNull
  Map<String, Object> metadata();

  @NonNull
  ExecutionMode mode();

  @NonNull
  String runId();

  @NonNull
  <U> Context<U> withBody(@NonNull U body);

  @NonNull
  Context<T> withMetadata(@NonNull String key, @Nullable Object value);

  @NonNull
  default JsonValue json() {
    throw new UnsupportedOperationException(
            "JSON value access is not available for this context implementation");
  }

  @NonNull
  default JsonValue asJsonValue() {
    return json();
  }

  @NonNull
  default JsonValue json(@Nullable Object value) {
    throw new UnsupportedOperationException(
            "JSON value access is not available for this context implementation");
  }

  @NonNull
  default JsonValue asJsonValue(@Nullable Object value) {
    return json(value);
  }

  @NonNull
  default Object eval(@NonNull String expression) {
    return eval(expression, Map.of());
  }

  @NonNull
  default Object eval(@NonNull String expression, @NonNull Map<String, Object> variables) {
    throw new UnsupportedOperationException(
            "Expression evaluation is not available for this context implementation");
  }

  @NonNull
  default TransactionRouting transactionRouting() {
    return TransactionRouting.LOCAL;
  }

  @Nullable
  default ExecutionListener executionListener() {
    return null;
  }

  @Nullable
  default DslSaga saga() {
    return null;
  }

  @Nullable
  default ExecutionTraceCollector executionTraceCollector() {
    return null;
  }

  @NonNull
  default Context<T> withTransactionRouting(@NonNull TransactionRouting routing) {
    return this;
  }

  @NonNull
  default Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return this;
  }

  @NonNull
  default Context<T> withSaga(@Nullable DslSaga saga) {
    return this;
  }

  @NonNull
  default Context<T> withExecutionTraceCollector(
          @Nullable ExecutionTraceCollector executionTraceCollector) {
    return this;
  }
}

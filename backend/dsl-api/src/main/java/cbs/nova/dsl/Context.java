package cbs.nova.dsl;

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

  /**
   * Routing hint for transaction execution. Defaults to {@link TransactionRouting#LOCAL}.
   */
  @NonNull
  default TransactionRouting transactionRouting() {
    return TransactionRouting.LOCAL;
  }

  /**
   * Returns the execution listener attached to this context, if any.
   */
  @Nullable
  default ExecutionListener executionListener() {
    return null;
  }

  /**
   * Returns a context with the given transaction routing hint.
   */
  @NonNull
  default Context<T> withTransactionRouting(@NonNull TransactionRouting routing) {
    return this;
  }

  /**
   * Returns a context with the given execution listener.
   */
  @NonNull
  default Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return this;
  }
}

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
   * Evaluates a lightweight sandboxed expression against this context.
   *
   * <p>
   * The expression may contain variable placeholders ({@code {name}}) or arithmetic/string
   * expressions ({@code ${a + b}}). The exact capabilities are implementation-specific, but callers
   * can rely on at least simple interpolation.
   *
   * @param expression
   *          the expression to evaluate
   * @return the evaluation result
   */
  @NonNull
  default Object eval(@NonNull String expression) {
    return eval(expression, Map.of());
  }

  /**
   * Evaluates a lightweight sandboxed expression using the supplied variables merged with the
   * context's own resolver stack (metadata and, when applicable, body fields).
   *
   * @param expression
   *          the expression to evaluate
   * @param variables
   *          additional variables to expose to the expression
   * @return the evaluation result
   */
  @NonNull
  default Object eval(@NonNull String expression, @NonNull Map<String, Object> variables) {
    throw new UnsupportedOperationException(
            "Expression evaluation is not available for this context implementation");
  }

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
   * Returns the optional Saga helper attached to this context. When present, successful
   * transactions automatically register their compensation action with this saga.
   */
  @Nullable
  default DslSaga saga() {
    return null;
  }

  /**
   * Returns the per-run execution-trace collector attached to this context, if any. When present,
   * rich contexts append execution-trace entries to it.
   */
  @Nullable
  default ExecutionTraceCollector executionTraceCollector() {
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

  /**
   * Returns a context with the given saga helper. Passing {@code null} clears any existing saga.
   */
  @NonNull
  default Context<T> withSaga(@Nullable DslSaga saga) {
    return this;
  }

  /**
   * Returns a context with the given per-run execution-trace collector attached. Rich contexts
   * resolve the collector from here rather than from a shared singleton.
   */
  @NonNull
  default Context<T> withExecutionTraceCollector(
          @Nullable ExecutionTraceCollector executionTraceCollector) {
    return this;
  }
}

package cbs.nova.dsl.transaction;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@RequiredArgsConstructor
public final class TransactionRichContext<T> implements TransactionContext<T> {

  private final Context<T> delegate;
  private final ContextFactory contextFactory;

  @Override
  public @NonNull T body() {
    return delegate.body();
  }

  @Override
  public @NonNull Map<String, Object> metadata() {
    return delegate.metadata();
  }

  @Override
  public @NonNull ExecutionMode mode() {
    return delegate.mode();
  }

  @Override
  public @NonNull String runId() {
    return delegate.runId();
  }

  @Override
  public @NonNull TransactionRouting transactionRouting() {
    return delegate.transactionRouting();
  }

  @Override
  public @Nullable ExecutionListener executionListener() {
    return delegate.executionListener();
  }

  @Override
  public @Nullable DslSaga saga() {
    return delegate.saga();
  }

  @Override
  public @Nullable ExecutionTraceCollector executionTraceCollector() {
    return delegate.executionTraceCollector();
  }

  @Override
  public @NonNull <U> Context<U> withBody(@NonNull U body) {
    return delegate.withBody(body);
  }

  @Override
  public @NonNull Context<T> withMetadata(@NonNull String key, Object value) {
    return delegate.withMetadata(key, value);
  }

  @Override
  public @NonNull Context<T> withTransactionRouting(@NonNull TransactionRouting routing) {
    return new TransactionRichContext<>(delegate.withTransactionRouting(routing), contextFactory);
  }

  @Override
  public @NonNull Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return new TransactionRichContext<>(delegate.withExecutionListener(listener), contextFactory);
  }

  @Override
  public @NonNull Context<T> withSaga(@Nullable DslSaga saga) {
    return new TransactionRichContext<>(delegate.withSaga(saga), contextFactory);
  }

  @Override
  public @NonNull Context<T> withExecutionTraceCollector(
          @Nullable ExecutionTraceCollector executionTraceCollector) {
    return new TransactionRichContext<>(
            delegate.withExecutionTraceCollector(executionTraceCollector), contextFactory);
  }

  private void trace(@NonNull String entry) {
    ExecutionTraceCollector collector = delegate.executionTraceCollector();
    if (collector != null) {
      collector.add(entry);
    }
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name) {
    Result<?> result = GlobalManager.globalManager().runHelper(name, delegate);
    trace("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input) {
    Result<?> result = GlobalManager.globalManager().runHelper(name,
            contextFactory.of(input, delegate.mode(), delegate.runId()));
    trace("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull MapInput input) {
    Result<?> result = GlobalManager.globalManager().runHelper(name,
            contextFactory.of(input, delegate.mode(), delegate.runId()));
    trace("called helper: " + name);
    return result;
  }
}

package cbs.nova.dsl.transaction;

import cbs.nova.dsl.BeanResolver;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.listener.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.model.MapInput;
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
  public @NonNull ExecutionListener executionListener() {
    return delegate.executionListener();
  }

  @Override
  public @NonNull DslSaga saga() {
    return delegate.saga();
  }

  @Override
  public @NonNull ExecutionTraceCollector executionTraceCollector() {
    return delegate.executionTraceCollector();
  }

  @Override
  public @NonNull HelperInterceptor helperInterceptor() {
    return delegate.helperInterceptor();
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

  @Override
  public @NonNull Context<T> withHelperInterceptor(@Nullable HelperInterceptor interceptor) {
    return new TransactionRichContext<>(delegate.withHelperInterceptor(interceptor),
            contextFactory);
  }

  private void trace(@NonNull String entry) {
    delegate.executionTraceCollector().add(entry);
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

  @Override
  public @NonNull BeanResolver beanResolver() {
    return delegate.beanResolver();
  }

  @Override
  public @NonNull Context<T> withBeanResolver(@Nullable BeanResolver beanResolver) {
    return new TransactionRichContext(delegate.withBeanResolver(beanResolver), contextFactory);
  }
}

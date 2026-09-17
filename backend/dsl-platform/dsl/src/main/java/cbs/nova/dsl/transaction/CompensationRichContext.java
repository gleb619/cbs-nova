package cbs.nova.dsl.transaction;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.CompensationContext;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.listener.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.model.MapInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public final class CompensationRichContext<T> implements CompensationContext<T> {

  private final Context<T> delegate;
  private final Throwable error;

  @Override
  public @NonNull Throwable error() {
    return error;
  }

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
    return new CompensationRichContext<>(delegate.withTransactionRouting(routing), error);
  }

  @Override
  public @NonNull Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return new CompensationRichContext<>(delegate.withExecutionListener(listener), error);
  }

  @Override
  public @NonNull Context<T> withSaga(@Nullable DslSaga saga) {
    return new CompensationRichContext<>(delegate.withSaga(saga), error);
  }

  @Override
  public @NonNull Context<T> withExecutionTraceCollector(
          @Nullable ExecutionTraceCollector executionTraceCollector) {
    return new CompensationRichContext<>(
            delegate.withExecutionTraceCollector(executionTraceCollector), error);
  }

  @Override
  public @NonNull Context<T> withHelperInterceptor(@Nullable HelperInterceptor interceptor) {
    return new CompensationRichContext<>(delegate.withHelperInterceptor(interceptor), error);
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
            SimpleContext.builder().body(input).mode(delegate.mode()).runId(delegate.runId())
                    .build());
    trace("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull MapInput input) {
    Result<?> result = GlobalManager.globalManager().runHelper(name,
            SimpleContext.builder().body(input).mode(delegate.mode()).runId(delegate.runId())
                    .build());
    trace("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull CompensationContext<T> log(@NonNull String message) {
    trace("compensation log: " + message);
    log.info("[DSL:{}][runId:{}] [compensation] {}", delegate.mode(), delegate.runId(), message);
    return this;
  }
}

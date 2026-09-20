package cbs.nova.dsl.process;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.BeanResolver;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.listener.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.transaction.TransactionRouting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public final class ProcessRichContext<T> implements ProcessContext<T> {

  private final Context<T> delegate;

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
    return new ProcessRichContext<>(delegate.withTransactionRouting(routing));
  }

  @Override
  public @NonNull Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return new ProcessRichContext<>(delegate.withExecutionListener(listener));
  }

  @Override
  public @NonNull Context<T> withSaga(@Nullable DslSaga saga) {
    return new ProcessRichContext<>(delegate.withSaga(saga));
  }

  @Override
  public @NonNull Context<T> withExecutionTraceCollector(
          @Nullable ExecutionTraceCollector executionTraceCollector) {
    return new ProcessRichContext<>(
            delegate.withExecutionTraceCollector(executionTraceCollector));
  }

  @Override
  public @NonNull Context<T> withHelperInterceptor(@Nullable HelperInterceptor interceptor) {
    return new ProcessRichContext<>(delegate.withHelperInterceptor(interceptor));
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
            SimpleContext.builder().body(input).metadata(delegate.metadata()).mode(delegate.mode())
                    .runId(delegate.runId())
                    .executionListener(delegate.executionListener()).build());
    trace("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull MapInput input) {
    Result<?> result = GlobalManager.globalManager().runHelper(name,
            SimpleContext.builder().body(input).metadata(delegate.metadata()).mode(delegate.mode())
                    .runId(delegate.runId())
                    .executionListener(delegate.executionListener()).build());
    trace("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Object input) {
    if (input instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return runHelper(name, typed);
    }
    if (input instanceof MapInput mapInput) {
      return runHelper(name, mapInput);
    }
    Result<?> result = GlobalManager.globalManager().runHelper(name,
            SimpleContext.builder().body(input).metadata(delegate.metadata()).mode(delegate.mode())
                    .runId(delegate.runId())
                    .executionListener(delegate.executionListener()).build());
    trace("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name) {
    Result<?> result = invokeTransaction(name, delegate.body());
    trace("executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name,
          @NonNull Map<String, Object> input) {
    Result<?> result = invokeTransaction(name, input);
    trace("executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name, @NonNull MapInput input) {
    Result<?> result = invokeTransaction(name, input);
    trace("executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name, @NonNull Object input) {
    if (input instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return runTransaction(name, typed);
    }
    if (input instanceof MapInput mapInput) {
      return runTransaction(name, mapInput);
    }
    Result<?> result = invokeTransaction(name, input);
    trace("executed transaction: " + name);
    return result;
  }

  private @NonNull Result<?> invokeTransaction(@NonNull String name, @NonNull Object input) {
    Context<Object> ctx = SimpleContext.builder().body(input).metadata(delegate.metadata())
            .mode(delegate.mode()).runId(delegate.runId())
            .transactionRouting(delegate.transactionRouting())
            .executionListener(delegate.executionListener()).saga(delegate.saga()).build()
            .withExecutionTraceCollector(delegate.executionTraceCollector())
            .withHelperInterceptor(delegate.helperInterceptor());
    if (delegate.transactionRouting() == TransactionRouting.TEMPORAL_ACTIVITY) {
      var invoker = GlobalManager.globalManager().transactionInvoker().orElse(null);
      if (invoker != null) {
        return invoker.invoke(name, input, ctx);
      }
    }
    return GlobalManager.globalManager().runTransaction(name, ctx);
  }

  @Override
  public @NonNull Result<?> complete(@NonNull Object result) {
    return Result.success(result);
  }

  @Override
  public void fail(@NonNull String reason) {
    throw new RuntimeException(reason);
  }

  @Override
  public void log(@NonNull String message) {
    log.info("[DSL:{}][runId:{}] {}", delegate.mode(), delegate.runId(), message);
  }

  @Override
  public @NonNull BeanResolver beanResolver() {
    return delegate.beanResolver();
  }

  @Override
  public @NonNull Context<T> withBeanResolver(@Nullable BeanResolver beanResolver) {
    return new ProcessRichContext(delegate.withBeanResolver(beanResolver));
  }
}

package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.MapInput;
import cbs.nova.dsl.ProcessContext;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.TransactionRouting;
import cbs.nova.dsl.config.ContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public final class ProcessRichContext<T> implements ProcessContext<T> {

  private final Context<T> delegate;
  private final ExecutionTraceCollector traceCollector;
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
  public @NonNull <U> Context<U> withBody(@NonNull U body) {
    return delegate.withBody(body);
  }

  @Override
  public @NonNull Context<T> withMetadata(@NonNull String key, Object value) {
    return delegate.withMetadata(key, value);
  }

  @Override
  public @NonNull Context<T> withTransactionRouting(@NonNull TransactionRouting routing) {
    return new ProcessRichContext<>(delegate.withTransactionRouting(routing), traceCollector,
            contextFactory);
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name) {
    Result<?> result = GlobalManager.getInstance().runHelper(name, delegate);
    traceCollector.add(delegate.runId(), "called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input) {
    Result<?> result = GlobalManager.getInstance().runHelper(name,
            contextFactory.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    traceCollector.add(delegate.runId(), "called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull MapInput input) {
    return runHelper(name, input.values());
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Object input) {
    if (input instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return runHelper(name, typed);
    }
    if (input instanceof MapInput mapInput) {
      return runHelper(name, mapInput.values());
    }
    Result<?> result = GlobalManager.getInstance().runHelper(name,
            contextFactory.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    traceCollector.add(delegate.runId(), "called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name) {
    Result<?> result = invokeTransaction(name, delegate.body());
    traceCollector.add(delegate.runId(), "executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name,
          @NonNull Map<String, Object> input) {
    Result<?> result = invokeTransaction(name, (Object) input);
    traceCollector.add(delegate.runId(), "executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name, @NonNull MapInput input) {
    return runTransaction(name, input.values());
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name, @NonNull Object input) {
    if (input instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return runTransaction(name, typed);
    }
    if (input instanceof MapInput mapInput) {
      return runTransaction(name, mapInput.values());
    }
    Result<?> result = invokeTransaction(name, input);
    traceCollector.add(delegate.runId(), "executed transaction: " + name);
    return result;
  }

  private @NonNull Result<?> invokeTransaction(@NonNull String name, @NonNull Object input) {
    Context<Object> ctx = contextFactory.of(input, delegate.metadata(), delegate.mode(),
            delegate.runId(), delegate.transactionRouting(), delegate.executionListener());
    if (delegate.transactionRouting() == TransactionRouting.TEMPORAL_ACTIVITY) {
      var invoker = GlobalManager.getInstance().transactionInvoker().orElse(null);
      if (invoker != null) {
        return invoker.invoke(name, input, ctx);
      }
    }
    return GlobalManager.getInstance().runTransaction(name, ctx);
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
  public @Nullable ExecutionListener executionListener() {
    return delegate.executionListener();
  }

  @Override
  public @NonNull Context<T> withExecutionListener(@NonNull ExecutionListener listener) {
    return new ProcessRichContext<>(delegate.withExecutionListener(listener), traceCollector,
            contextFactory);
  }
}

package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ProcessContext;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

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
  public @NonNull <U> Context<U> withBody(@NonNull U body) {
    return delegate.withBody(body);
  }

  @Override
  public @NonNull Context<T> withMetadata(@NonNull String key, Object value) {
    return delegate.withMetadata(key, value);
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name) {
    Result<?> result = GlobalManager.getInstance().runHelper(name, delegate);
    traceCollector.add("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input) {
    Result<?> result = GlobalManager.getInstance().runHelper(name,
            contextFactory.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    traceCollector.add("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Object input) {
    if (input instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return runHelper(name, typed);
    }
    Result<?> result = GlobalManager.getInstance().runHelper(name,
            contextFactory.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    traceCollector.add("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name) {
    Result<?> result = GlobalManager.getInstance().runTransaction(name, delegate);
    traceCollector.add("executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name,
          @NonNull Map<String, Object> input) {
    Result<?> result = GlobalManager.getInstance().runTransaction(name,
            contextFactory.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    traceCollector.add("executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name, @NonNull Object input) {
    if (input instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return runTransaction(name, typed);
    }
    Result<?> result = GlobalManager.getInstance().runTransaction(name,
            contextFactory.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    traceCollector.add("executed transaction: " + name);
    return result;
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
}

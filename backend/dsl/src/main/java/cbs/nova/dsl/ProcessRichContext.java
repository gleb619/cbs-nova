package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class ProcessRichContext<T> implements ProcessContext<T> {

  private final Context<T> delegate;

  ProcessRichContext(@NonNull Context<T> delegate) {
    this.delegate = delegate;
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
    ExecutionTraceCollector.add("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input) {
    Result<?> result = GlobalManager.getInstance().runHelper(name,
            SimpleContext.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    ExecutionTraceCollector.add("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name) {
    Result<?> result = GlobalManager.getInstance().runTransaction(name, delegate);
    ExecutionTraceCollector.add("executed transaction: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name,
          @NonNull Map<String, Object> input) {
    Result<?> result = GlobalManager.getInstance().runTransaction(name,
            SimpleContext.of(input, delegate.metadata(), delegate.mode(), delegate.runId()));
    ExecutionTraceCollector.add("executed transaction: " + name);
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
    System.out.println("[DSL:" + delegate.mode() + "][runId:" + delegate.runId() + "] " + message);
  }
}

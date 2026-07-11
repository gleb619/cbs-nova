package cbs.nova.dsl.function;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.FunctionContext;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import java.util.Map;
import org.jspecify.annotations.NonNull;

public final class FunctionRichContext<T> implements FunctionContext<T> {

  private final Context<T> delegate;

  public FunctionRichContext(@NonNull Context<T> delegate) {
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
    ExecutionTraceCollector.getInstance().add("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input) {
    Result<?> result = GlobalManager.getInstance().runHelper(name,
            SimpleContext.getInstance().of(input, delegate.mode(), delegate.runId()));
    ExecutionTraceCollector.getInstance().add("called helper: " + name);
    return result;
  }
}

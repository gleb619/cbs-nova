package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class TransactionRichContext<T> implements TransactionContext<T> {

  private final Context<T> delegate;

  TransactionRichContext(@NonNull Context<T> delegate) {
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
    return GlobalManager.getInstance().runHelper(name, delegate);
  }

  @Override
  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input) {
    return GlobalManager.getInstance().runHelper(name,
            SimpleContext.of(input, delegate.mode(), delegate.runId()));
  }
}

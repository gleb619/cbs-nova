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
            SimpleContext.of(input, delegate.mode()));
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name) {
    return GlobalManager.getInstance().runTransaction(name, delegate);
  }

  @Override
  public @NonNull Result<?> runTransaction(@NonNull String name,
          @NonNull Map<String, Object> input) {
    return GlobalManager.getInstance().runTransaction(name,
            SimpleContext.of(input, delegate.mode()));
  }

  @Override
  public void fail(@NonNull String reason) {
    throw new RuntimeException(reason);
  }

  @Override
  public void log(@NonNull String message) {
    System.out.println("[DSL:" + delegate.mode() + "] " + message);
  }
}

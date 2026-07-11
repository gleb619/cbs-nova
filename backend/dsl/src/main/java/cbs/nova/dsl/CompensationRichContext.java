package cbs.nova.dsl;

import cbs.nova.dsl.config.ContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public final class CompensationRichContext<T> implements CompensationContext<T> {

  private final Context<T> delegate;
  private final Throwable error;
  private final ExecutionTraceCollector traceCollector;
  private final ContextFactory contextFactory;

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
            contextFactory.of(input, delegate.mode(), delegate.runId()));
    traceCollector.add("called helper: " + name);
    return result;
  }

  @Override
  public @NonNull CompensationContext<T> log(@NonNull String message) {
    traceCollector.add("compensation log: " + message);
    log.info("[DSL:{}][runId:{}] [compensation] {}", delegate.mode(), delegate.runId(), message);
    return this;
  }
}

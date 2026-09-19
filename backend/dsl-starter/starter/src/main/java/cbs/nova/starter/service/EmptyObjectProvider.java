package cbs.nova.starter.service;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Minimal {@link ObjectProvider} stub that returns {@code null} from {@link #getIfAvailable()}.
 * Used by {@link TemporalDslProcessService#withDefaults} (and other test/utility factories) so the
 * production constructor signature stays consistent without pulling in a real application context.
 * Only the methods actually called from production code are overridden; everything else falls
 * through to {@link ObjectProvider}'s default {@code null}/empty-stream behavior.
 */
public final class EmptyObjectProvider<T> implements ObjectProvider<T> {

  private final Class<T> type;

  private EmptyObjectProvider(Class<T> type) {
    this.type = type;
  }

  public static <T> ObjectProvider<T> of(Class<T> type) {
    return new EmptyObjectProvider<>(type);
  }

  @Override
  public T getObject() {
    throw new IllegalStateException("No bean of type " + type.getName() + " available");
  }

  @Override
  public @Nullable T getIfAvailable() {
    return null;
  }

  @Override
  public @Nullable T getIfUnique() {
    return null;
  }

  @Override
  public Stream<T> stream() {
    return Stream.empty();
  }

  @Override
  public Stream<T> orderedStream() {
    return Stream.empty();
  }
}

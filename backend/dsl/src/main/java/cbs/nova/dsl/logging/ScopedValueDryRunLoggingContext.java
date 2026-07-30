package cbs.nova.dsl.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * {@link DryRunLoggingContext} implementation based on {@link java.lang.ScopedValue}.
 *
 * <p>
 * Requires JDK 21+ (the project toolchain is JDK 25). Because scoped values can only be bound for
 * the duration of a {@link ScopedValue#where(ScopedValue, Object) ScopedValue.Carrier} block,
 * {@link #setRunId(String)} and {@link #clearRunId()} are unsupported; callers must use
 * {@link #runWithRunId(String, Runnable)}.
 */
public final class ScopedValueDryRunLoggingContext implements DryRunLoggingContext {

  private static final ScopedValue<String> RUN_ID = ScopedValue.newInstance();

  @Override
  public void runWithRunId(@NonNull String runId, @NonNull Runnable action) {
    ScopedValue.where(RUN_ID, runId).run(action);
  }

  @Override
  public void setRunId(@Nullable String runId) {
    throw new UnsupportedOperationException(
            "ScopedValue based context does not support setRunId outside of a scope; use runWithRunId");
  }

  @Override
  public void clearRunId() {
    // Scoped values are automatically cleared when the ScopedValue.Carrier block exits.
  }

  @Override
  public @Nullable String currentRunId() {
    return RUN_ID.isBound() ? RUN_ID.get() : null;
  }
}

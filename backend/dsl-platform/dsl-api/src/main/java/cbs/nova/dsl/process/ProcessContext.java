package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.MapInput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface ProcessContext<T> extends Context<T> {

  @NonNull
  Result<?> runHelper(@NonNull String name);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull MapInput input);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Object input);

  @NonNull
  Result<?> runTransaction(@NonNull String name);

  @NonNull
  Result<?> runTransaction(@NonNull String name, @NonNull Map<String, Object> input);

  @NonNull
  Result<?> runTransaction(@NonNull String name, @NonNull MapInput input);

  @NonNull
  Result<?> runTransaction(@NonNull String name, @NonNull Object input);

  @NonNull
  Result<?> complete(@NonNull Object result);

  void fail(@NonNull String reason);

  void log(@NonNull String message);

  /**
   * Await a Temporal signal declared by the process.
   *
   * <p>
   * In {@code run} mode inside a Temporal workflow this blocks until the signal arrives. In preview
   * / hierarchy / explain mode it resolves against the seeded preview signal timeline
   * ({@link cbs.nova.dsl.process.PreviewSignalBuffer}) — never blocking on wall-clock time. When no
   * buffer is installed at all (e.g. a bare in-process run without any pipeline), it throws
   * {@link IllegalStateException}; when a preview buffer exists but the name was not seeded, it
   * throws {@link cbs.nova.dsl.process.SignalNotSeededException}.
   */
  @NonNull
  default <T> T awaitSignal(@NonNull String name, @NonNull Class<T> type) {
    SignalAwaiter awaiter = metadata(Constants.SIGNAL_AWAITER_METADATA_KEY);
    if (awaiter == null) {
      throw new IllegalStateException(
              "Signal awaiting is not available in this context (missing SignalAwaiter)");
    }
    return awaiter.awaitSignal(name, type);
  }

  @Nullable
  default <T> T getSignalPayload(@NonNull String name, @NonNull Class<T> type) {
    SignalAwaiter awaiter = metadata(Constants.SIGNAL_AWAITER_METADATA_KEY);
    if (awaiter == null) {
      return null;
    }
    return awaiter.getSignalPayload(name, type);
  }

  default boolean signalReceived(@NonNull String name) {
    SignalAwaiter awaiter = metadata(Constants.SIGNAL_AWAITER_METADATA_KEY);
    if (awaiter == null) {
      return false;
    }
    return awaiter.signalReceived(name);
  }
}

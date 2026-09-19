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
   * Await a Temporal signal declared by the process. Only available when the process is running
   * inside a Temporal workflow with signal support; otherwise throws.
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

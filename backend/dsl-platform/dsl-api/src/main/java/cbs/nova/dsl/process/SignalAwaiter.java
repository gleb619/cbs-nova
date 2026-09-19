package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface SignalAwaiter {

  @NonNull
  <T> T awaitSignal(@NonNull String name, @NonNull Class<T> type);

  @Nullable
  <T> T getSignalPayload(@NonNull String name, @NonNull Class<T> type);

  boolean signalReceived(@NonNull String name);
}

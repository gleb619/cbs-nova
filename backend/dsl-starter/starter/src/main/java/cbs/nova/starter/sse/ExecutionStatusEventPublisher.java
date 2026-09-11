package cbs.nova.starter.sse;

import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface ExecutionStatusEventPublisher {

  void publish(@NonNull String id, @NonNull String status);
}

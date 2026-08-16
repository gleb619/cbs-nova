package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface ProcessCompensation {

  void accept(@NonNull Context<?> ctx, @NonNull Throwable error);
}

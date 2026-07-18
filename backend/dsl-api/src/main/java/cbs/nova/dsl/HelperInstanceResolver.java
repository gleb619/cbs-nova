package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface HelperInstanceResolver {

  @NonNull
  Executable<?, ?> resolve(@NonNull Class<?> helperClass);
}

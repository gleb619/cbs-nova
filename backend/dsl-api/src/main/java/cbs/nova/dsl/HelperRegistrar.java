package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface HelperRegistrar {
  void register(@NonNull String name, @NonNull Executable<?, ?> helper);
}

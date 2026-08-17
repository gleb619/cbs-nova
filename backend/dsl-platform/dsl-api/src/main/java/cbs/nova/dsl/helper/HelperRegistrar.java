package cbs.nova.dsl.helper;

import cbs.nova.dsl.Executable;
import org.jspecify.annotations.NonNull;

public interface HelperRegistrar {

  void register(@NonNull String name, @NonNull Executable<?, ?> helper);
}

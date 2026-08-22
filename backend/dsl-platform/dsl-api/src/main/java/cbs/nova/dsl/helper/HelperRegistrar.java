package cbs.nova.dsl.helper;

import cbs.nova.dsl.Executable;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface HelperRegistrar {

  default void register(@NonNull String name, @NonNull Executable<?, ?> helper) {
    register(name, () -> helper);
  }

  void register(@NonNull String name, @NonNull Supplier<Executable<?, ?>> helperSupplier);

}

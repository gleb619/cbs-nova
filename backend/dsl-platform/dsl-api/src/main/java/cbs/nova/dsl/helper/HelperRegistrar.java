package cbs.nova.dsl.helper;

import cbs.nova.dsl.Executable;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

@FunctionalInterface
public interface HelperRegistrar {

  default void register(@NonNull String name, @NonNull Executable<?, ?> helper) {
    register(name, () -> helper);
  }

  void register(@NonNull String name, @NonNull Supplier<Executable<?, ?>> helperSupplier);

}

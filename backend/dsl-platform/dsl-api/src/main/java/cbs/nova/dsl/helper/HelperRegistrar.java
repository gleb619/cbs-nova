package cbs.nova.dsl.helper;

import cbs.nova.dsl.Executable;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

public interface HelperRegistrar {

  void register(@NonNull String name, @NonNull Executable<?, ?> helper);

  void register(@NonNull String name, @NonNull Supplier<Executable<?, ?>> helperSupplier);

}

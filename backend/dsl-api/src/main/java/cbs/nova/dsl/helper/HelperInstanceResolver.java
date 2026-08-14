package cbs.nova.dsl.helper;

import cbs.nova.dsl.Executable;
import org.jspecify.annotations.NonNull;

public interface HelperInstanceResolver {

  @NonNull
  Executable<?, ?> resolve(@NonNull Class<?> helperClass);
}

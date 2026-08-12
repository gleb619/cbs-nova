package cbs.nova.starter.core.recorder;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface MockResolver {

  @Nullable
  Object findMock(@NonNull String type, @NonNull String target, @NonNull String operation);
}

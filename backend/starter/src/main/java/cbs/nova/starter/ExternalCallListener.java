package cbs.nova.starter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface ExternalCallListener {
  void onCall(@NonNull String type, @NonNull String target, @NonNull String operation,
          @Nullable Object payload);
}

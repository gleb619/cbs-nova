package cbs.nova.starter.core.recorder;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Mock resolver backed by a plain signature -> mock map. Signatures are formed as
 * {@code type:target:operation} to match the run-scoped recorder mock format.
 */
public final class MapBasedMockResolver implements MockResolver {

  private final Map<String, Object> mocks;

  public MapBasedMockResolver(@NonNull Map<String, Object> mocks) {
    this.mocks = Map.copyOf(mocks);
  }

  @Override
  public @Nullable Object findMock(@NonNull String type, @NonNull String target,
          @NonNull String operation) {
    return mocks.get(type + ":" + target + ":" + operation);
  }
}

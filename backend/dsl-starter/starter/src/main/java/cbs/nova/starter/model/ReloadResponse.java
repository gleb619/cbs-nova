package cbs.nova.starter.model;

import cbs.nova.dsl.LoadResult;
import org.jspecify.annotations.NonNull;

/**
 * Success response for the DSL reload endpoint, carrying the {@link LoadResult} drilldown so
 * operators and the workbench can see exactly what a reload loaded (counts and names per type).
 */
public record ReloadResponse(
        @NonNull String sourceDir,
        @NonNull LoadResult load) {

  public int total() {
    return load.total();
  }
}

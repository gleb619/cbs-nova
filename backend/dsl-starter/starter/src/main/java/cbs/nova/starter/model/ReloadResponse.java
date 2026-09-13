package cbs.nova.starter.model;

import cbs.nova.dsl.LoadResult;
import org.jspecify.annotations.NonNull;


public record ReloadResponse(
        @NonNull String sourceDir,
        @NonNull LoadResult load) {

  public int total() {
    return load.total();
  }
}

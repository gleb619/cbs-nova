package cbs.nova.starter.model;

import cbs.nova.dsl.PreviewReport;
import org.jspecify.annotations.NonNull;

public final class PreviewModels {

  public record PreviewCacheEntry(
          @NonNull PreviewReport report,
          long timestamp,
          long ttlMs) {
  
  }

  public record PreviewCacheKey(
          @NonNull String processName,
          @NonNull String dslDescriptorHash,
          @NonNull String inputHash) {
  
  }
}

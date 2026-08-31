package cbs.nova.starter.model;

import cbs.nova.dsl.LoadResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public final class VcsModels {

  public record DraftRequest(
          String name,
          String type,
          String status,
          String version,
          String taskQueue) {

  }

  public record DraftResponse(
          String name,
          String status,
          String location,
          boolean reloaded,
          LoadResult loadResult,
          @JsonInclude(JsonInclude.Include.NON_NULL) String reloadError,
          @JsonInclude(JsonInclude.Include.NON_NULL) List<CompileDiagnostic> diagnostics) {

    public DraftResponse(String name, String status, String location, boolean reloaded,
            LoadResult loadResult) {
      this(name, status, location, reloaded, loadResult, null, null);
    }

  }

  public record DraftSummary(
          String name,
          String type,
          String status,
          String version,
          long updatedAt) {

  }

  public record DefinitionHistoryEntry(
          String timestamp,
          long timestampMillis,
          long sizeBytes,
          long lastModifiedMillis) {

  }

}

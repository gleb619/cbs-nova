package cbs.nova.dsl.builder.model;

import cbs.nova.dsl.model.DiffHunk;
import cbs.nova.dsl.model.LoadResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VcsModels {

  public record DraftRequest(
          String name,
          String type,
          String status,
          String version,
          String taskQueue,
          @JsonInclude(JsonInclude.Include.NON_NULL) String source,
          @JsonInclude(JsonInclude.Include.NON_NULL) Long savedAt) {

  }

  public record DraftResponse(
          String name,
          String status,
          String location,
          boolean reloaded,
          LoadResult loadResult,
          @JsonInclude(JsonInclude.Include.NON_NULL) String reloadError,
          @JsonInclude(JsonInclude.Include.NON_NULL) List<CompileDiagnostic> diagnostics,
          @JsonInclude(JsonInclude.Include.NON_NULL) Long savedAt) {

    public DraftResponse(String name, String status, String location, boolean reloaded,
            LoadResult loadResult) {
      this(name, status, location, reloaded, loadResult, null, null, null);
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

  public record HistoryDiffResponse(
          String name,
          String timestamp,
          @JsonInclude(JsonInclude.Include.NON_NULL) String before,
          String after,
          List<DiffHunk> hunks,
          boolean truncated) {

  }

  public record DefinitionBundleEntry(
          DraftRequest definition,
          String source) {

  }

  public record DefinitionBundle(
          int formatVersion,
          String engineVersion,
          String exportedAt,
          List<DefinitionBundleEntry> definitions) {

  }

  public record ImportEntryResult(
          String name,
          String outcome,
          @JsonInclude(JsonInclude.Include.NON_NULL) String message) {

  }

  public record ImportBundleResult(
          boolean dryRun,
          boolean reloaded,
          int published,
          int failed,
          List<ImportEntryResult> results,
          @JsonInclude(JsonInclude.Include.NON_NULL) String reloadError,
          @JsonInclude(JsonInclude.Include.NON_NULL) List<CompileDiagnostic> diagnostics) {

  }

  /**
   * Per spec §3.3 + invariant I2: publish = atomic commit. The caller passes the dirty paths
   * reported by {@code /api/dsl/vcs/status}, a commit message, and the actor identity.
   */
  public record CommitRequest(
          List<String> paths,
          @JsonInclude(JsonInclude.Include.NON_NULL) String message,
          @JsonInclude(JsonInclude.Include.NON_NULL) String authorName,
          @JsonInclude(JsonInclude.Include.NON_NULL) String authorEmail) {

  }

  public record CommitResult(
          String commitId,
          List<String> paths,
          long timestampMillis) {

  }

  /** Discard = checkout HEAD (or remove an untracked/added file). See spec §3.3. */
  public record DiscardRequest(
          List<String> paths) {

  }

  public record DiscardResult(
          List<String> discarded) {

  }

  /** History entry per spec §3.3: git log -- path. */
  public record LogEntry(
          String commitId,
          long timestampMillis,
          String author,
          String message) {

  }
}

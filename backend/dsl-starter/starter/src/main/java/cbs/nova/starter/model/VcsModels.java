package cbs.nova.starter.model;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.CompileDiagnostic;

import cbs.nova.dsl.model.DiffHunk;
import cbs.nova.dsl.model.LoadResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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

  /**
   * Status-only view of a single definition, returned by {@code GET /api/dsl/drafts/{name}}. File
   * content is served by {@code GET /api/dsl/files/by-name/{name}} instead.
   */
  public record DraftDetail(
          String name,
          String type,
          String status) {

  }

  public record DraftResponse(
          String name,
          String status,
          String location,
          boolean reloaded,
          LoadResult loadResult,
          @JsonInclude(JsonInclude.Include.NON_NULL) String reloadError,
          @JsonInclude(JsonInclude.Include.NON_NULL) List<CompileDiagnostic> diagnostics,
          @JsonInclude(JsonInclude.Include.NON_NULL) Long savedAt,
          @JsonInclude(JsonInclude.Include.NON_NULL) String commitId) {

    public DraftResponse(String name, String status, String location, boolean reloaded,
            LoadResult loadResult) {
      this(name, status, location, reloaded, loadResult, null, null, null, null);
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
          List<DefinitionBundleEntry> definitions,
          @JsonInclude(JsonInclude.Include.NON_NULL) String digest) {

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
   * Mirror of {@code cbs.nova.dsl.builder.model.VcsModels.CommitRequest} — atomic per-request
   * publish per spec §3.3 + invariant I2. The starter sets {@code message} and {@code author*} from
   * the caller's audit row before forwarding to the builder.
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
          long timestampMillis,
          @JsonInclude(JsonInclude.Include.NON_NULL) Boolean pushed,
          @JsonInclude(JsonInclude.Include.NON_NULL) String pushError) {

  }

  /** Discard = checkout HEAD (or remove an untracked / added file). */
  public record DiscardRequest(
          List<String> paths) {

  }

  public record DiscardResult(
          List<String> discarded) {

  }

  /**
   * dsl-builder workspace description from {@code GET /api/dsl/workspace}: the configured
   * {@code mode} ({@code SHARED} or {@code WORKTREE}) and the actual {@code workTree} directory
   * backing DSL file I/O on the builder host.
   */
  public record WorkspaceInfo(
          String mode,
          String workTree) {

  }

  /** History entry per spec §3.3: {@code git log -- path}, newest-first. */
  public record LogEntry(
          String commitId,
          long timestampMillis,
          String author,
          String message) {

  }

  /**
   * Snapshot of the current drafts workspace setup. Returned by
   * {@code GET /api/dsl/drafts/metadata}.
   *
   * @param draftCount
   *          number of DSL source files that currently have a git change (uncommitted draft)
   * @param sourcePath
   *          the actual directory backing DSL file I/O — the dsl-builder workspace when the builder
   *          is reachable, else the configured {@code cbs.dsl.source-dir}; forward-slash separated;
   *          empty when neither is known
   * @param sizeMb
   *          total size of the changed source files in MB, rounded to two decimal places; {@code
   *          0.0} when there are no drafts; {@code null} when sizes cannot be resolved (e.g. the
   *          builder is unavailable)
   * @param gitBranch
   *          name of the current git branch; {@code null} when git is disabled or no repo exists
   * @param gitEnabled
   *          whether git integration is enabled for the DSL workspace
   * @param statusCacheTtlSeconds
   *          configured git status cache TTL
   * @param workspaceMode
   *          dsl-builder workspace mode ({@code SHARED} or {@code WORKTREE}) as reported by
   *          {@code GET /api/dsl/workspace}; {@code null} when the builder is unreachable
   */
  public record DraftsMetadata(
          int draftCount,
          String sourcePath,
          @JsonInclude(JsonInclude.Include.NON_NULL) Double sizeMb,
          @JsonInclude(JsonInclude.Include.NON_NULL) String gitBranch,
          boolean gitEnabled,
          int statusCacheTtlSeconds,
          @JsonInclude(JsonInclude.Include.NON_NULL) String workspaceMode) {

  }

}

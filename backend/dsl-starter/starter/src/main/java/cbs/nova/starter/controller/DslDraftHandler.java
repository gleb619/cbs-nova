package cbs.nova.starter.controller;

import static cbs.nova.starter.core.StarterConstants.BUNDLE_MAX_DEFINITIONS;
import static cbs.nova.starter.core.StarterConstants.WORKBENCH_DRAFTS_DIR;
import static cbs.nova.starter.core.StarterConstants.WORKBENCH_PUBLISHED_DIR;

import lombok.AllArgsConstructor;

import cbs.nova.dsl.model.DiffHunk;
import cbs.nova.dsl.model.LoadResult;
import cbs.nova.dsl.exception.ValidationException;
import cbs.nova.dsl.utils.LineDiff;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.VcsModels.HistoryDiffResponse;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import cbs.nova.starter.model.VcsModels.LogEntry;
import cbs.nova.dsl.model.CompileDiagnostic;
import cbs.nova.starter.model.CompileDiagnosticSource;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.service.CorrelationId;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslSourcePathResolver;
import tools.jackson.core.JacksonException;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "csb.dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
@AllArgsConstructor
public class DslDraftHandler {

  static final String ACTION_DRAFT_WRITE = "DRAFT_WRITE";
  static final String ACTION_DEFINITION_PUBLISH = "DEFINITION_PUBLISH";
  static final String ACTION_DRAFT_BULK_WRITE = "DRAFT_BULK_WRITE";
  static final String ACTION_DRAFT_DISCARD = "DRAFT_DISCARD";
  static final String ACTION_DRAFT_COMMIT = "DEFINITION_PUBLISH_COMMIT";
  static final String ACTION_DRAFT_RESTORE = "DRAFT_RESTORE";
  static final String GIT_NOT_CONFIGURED_CODE = "GIT_NOT_CONFIGURED";
  static final int HISTORY_LIMIT = 50;
  static final long HISTORY_SIZE_UNKNOWN = -1L;
  static final String DEFAULT_LOG_LIMIT = "20";
  static final int MAX_LOG_LIMIT = 200;
  static final String GIT_REQUIRES_BUILDER_CODE = "GIT_REQUIRES_BUILDER";
  static final String COMMIT_ERROR_DETAIL = "commitError";

  private final DslProperties dslProperties;
  private final DslReloadHandler reloadHandler;
  private final DslDefinitionHistoryService historyService;
  private final ObjectMapper objectMapper;
  private final DslDefinitionBundleService bundleService;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;
  private final ObjectProvider<CompileDiagnosticRecordRepository> compileDiagnosticRepositoryProvider;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final ObjectProvider<RoleResolver> roleResolverProvider;
  private final ObjectProvider<DslSourcePathResolver> sourcePathResolverProvider;
  private final ObjectProvider<DslGitStatusResolver> gitStatusResolverProvider;

  public ServerResponse save(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "name is required"));
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null, null, null,
                      null, null));
    }
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "drafts directory not configured"));
      return dir.response();
    }
    var payload = withStatus(withSavedAt(body), "Draft");
    var builder = builderClient();
    if (builder != null) {
      try {
        DraftResponse saved = builder.saveDraft(name, payload);
        audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_SUCCESS,
                Map.of("location", String.valueOf(saved.location())));
        publishEventBestEffort(new DomainEvent.DraftSaved(
                name, payload.version(), payload.taskQueue(), null, correlationIdOf(request)));
        log.info("[DSL drafts] saved {} via DSL builder", name);
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(saved);
      } catch (RuntimeException e) {
        audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
                Map.of("error", String.valueOf(e.getMessage())));
        throw e;
      }
    }
    try {
      Path file = writePayload(dir.path().resolve(WORKBENCH_DRAFTS_DIR), payload);
      audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_SUCCESS,
              Map.of("location", file.toString()));
      publishEventBestEffort(new DomainEvent.DraftSaved(
              name, payload.version(), payload.taskQueue(), null, correlationIdOf(request)));
      log.info("[DSL drafts] saved {} to {}", name, file);
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(new DraftResponse(name, "Draft", file.toString(), false, LoadResult.empty(),
                      null, null, payload.savedAt(), null));
    } catch (IOException | RuntimeException e) {
      audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse publish(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "name is required"));
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null, null, null,
                      null, null));
    }
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "drafts directory not configured"));
      return dir.response();
    }
    if (dslProperties.approval().required() && !resolveRole(request).satisfies(Role.OPERATOR)) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "publish requires approval"));
      return error(HttpStatus.FORBIDDEN,
              new ErrorResponse(StarterConstants.FORBIDDEN_CODE, "publish requires approval",
                      name, null, null, null, null, null, null));
    }
    return publishPayload(request, name, body);
  }

  /**
   * The publish flow itself (write published marker / delegate to the builder, snapshot history,
   * reload, audit, domain event) — shared by {@link #publish} and the T568 change-request approve
   * path, which replays an approved snapshot instead of the request body. Performs NO approval-gate
   * check: gating is the caller's responsibility.
   */
  public ServerResponse publishPayload(ServerRequest request, String name, DraftRequest body)
          throws IOException {
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "drafts directory not configured"));
      return dir.response();
    }
    var payload = withStatus(body, "Published");
    var builder = builderClient();
    if (builder != null) {
      try {
        var published = builder.publishDraft(name, payload);
        log.info("[DSL drafts] published {} via DSL builder", name);
        DraftResponse response = finishPublish(name, published.location(), dir.path());
        boolean success = response.reloadError() == null;
        // Spec §3.3 + I2: publish is atomic per request. The reload must hold *before* we
        // commit the working tree; if reload failed or the file isn't actually dirty in
        // git, leave the draft uncommitted so the user sees the diagnostic and retries.
        String commitId = null;
        if (success && response.reloaded()) {
          commitId = commitIfDirty(builder, request, name);
        }
        DraftResponse withCommit = commitId == null
                ? response
                : new DraftResponse(response.name(), response.status(), response.location(),
                        response.reloaded(), response.loadResult(), response.reloadError(),
                        response.diagnostics(), response.savedAt(), commitId);
        audit(request, ACTION_DEFINITION_PUBLISH, name,
                success ? StarterConstants.OUTCOME_SUCCESS : StarterConstants.OUTCOME_FAILURE,
                Map.of("location", String.valueOf(published.location()),
                        "reloaded", withCommit.reloaded(),
                        "commitId", String.valueOf(commitId),
                        "error", success ? "" : String.valueOf(withCommit.reloadError())));
        publishEventBestEffort(new DomainEvent.DraftPublished(
                name, payload.version(), payload.taskQueue(),
                withCommit.reloaded(), withCommit.location(), null,
                correlationIdOf(request)));
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(withCommit);
      } catch (DslCompilationException e) {
        recordDiagnostics(CompileDiagnosticSource.PUBLISH, name, e.diagnostics());
        audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
                Map.of("error", String.valueOf(e.getMessage())));
        throw e;
      } catch (IOException | RuntimeException e) {
        audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
                Map.of("error", String.valueOf(e.getMessage())));
        throw e;
      }
    }
    try {
      historyService.snapshotBeforePublish(dir.path(), name);
      Path file = writePayload(dir.path().resolve(WORKBENCH_PUBLISHED_DIR), payload);
      log.info("[DSL drafts] published {} to {}", name, file);
      DraftResponse response = finishPublish(name, file.toString(), dir.path());
      boolean success = response.reloadError() == null;
      audit(request, ACTION_DEFINITION_PUBLISH, name,
              success ? StarterConstants.OUTCOME_SUCCESS : StarterConstants.OUTCOME_FAILURE,
              Map.of("location", file.toString(),
                      "reloaded", response.reloaded(),
                      "error", success ? "" : String.valueOf(response.reloadError())));
      publishEventBestEffort(new DomainEvent.DraftPublished(
              name, payload.version(), payload.taskQueue(),
              response.reloaded(), response.location(), null,
              correlationIdOf(request)));
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(response);
    } catch (IOException | RuntimeException e) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse history(ServerRequest request) {
    String name = request.pathVariable("name");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var builder = builderClient();
    if (builder != null) {
      var sourcePath = resolveSourcePath(name);
      if (sourcePath.isPresent()) {
        try {
          String path = sourcePath.get();
          List<LogEntry> entries = builder.vcsLog(path, HISTORY_LIMIT);
          List<DefinitionHistoryEntry> history = entries.stream()
                  .map(e -> new DefinitionHistoryEntry(e.commitId(), e.timestampMillis(),
                          HISTORY_SIZE_UNKNOWN, e.timestampMillis()))
                  .toList();
          return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(history);
        } catch (RuntimeException e) {
          if (isGitNotConfigured(e)) {
            log.debug("[DSL drafts] git not configured for {} — falling back to JSON history",
                    name);
            return historyViaBuilderJson(name, builder);
          }
          throw e;
        }
      }
      return historyViaBuilderJson(name, builder);
    }
    List<DefinitionHistoryEntry> entries = historyService.list(dir.path(), name);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(entries);
  }

  public ServerResponse historyEntry(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    String timestamp = request.pathVariable("timestamp");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var builder = builderClient();
    if (builder != null) {
      var sourcePath = resolveSourcePath(name);
      if (sourcePath.isPresent() && isGitCommitId(timestamp)) {
        String path = sourcePath.get();
        try {
          String content = builder.vcsShow(path, timestamp);
          DraftRequest meta = readDraftMetadataIgnoring404(builder, name);
          DraftRequest response = new DraftRequest(
                  name,
                  meta != null ? meta.type() : null,
                  "History",
                  meta != null ? meta.version() : null,
                  meta != null ? meta.taskQueue() : null,
                  content,
                  null);
          return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (BuilderApiException e) {
          if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return historyEntryNotFound(name, timestamp);
          }
          if (isGitNotConfigured(e)) {
            return historyEntryViaBuilderJson(name, timestamp, builder);
          }
          throw e;
        } catch (RuntimeException e) {
          if (isGitNotConfigured(e)) {
            return historyEntryViaBuilderJson(name, timestamp, builder);
          }
          throw e;
        }
      }
      return historyEntryViaBuilderJson(name, timestamp, builder);
    }
    var entry = historyService.readEntry(dir.path(), name, timestamp);
    if (entry.isEmpty()) {
      return historyEntryNotFound(name, timestamp);
    }
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(entry.get());
  }

  /**
   * Git-backed diff compares the file content at the requested commit against the current
   * working-tree content of the definition's source path. The local-filesystem branch compares the
   * stored published marker against the requested JSON history snapshot.
   */
  public ServerResponse historyDiff(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    String timestamp = request.pathVariable("timestamp");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var builder = builderClient();
    if (builder != null) {
      var sourcePath = resolveSourcePath(name);
      if (sourcePath.isPresent() && isGitCommitId(timestamp)) {
        String path = sourcePath.get();
        try {
          String before = builder.vcsShow(path, timestamp);
          String after = builder.readFile(path).content();
          LineDiff.Result result = LineDiff.diff(before, after, StarterConstants.DEFAULT_MAX_HUNKS,
                  StarterConstants.LINE_DIFF_CONTEXT_LINES);
          return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                  .body(new HistoryDiffResponse(name, timestamp, before, after, result.hunks(),
                          result.truncated()));
        } catch (BuilderApiException e) {
          if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return historyEntryNotFound(name, timestamp);
          }
          if (isGitNotConfigured(e)) {
            return historyDiffViaBuilderJson(name, timestamp, builder);
          }
          throw e;
        } catch (RuntimeException e) {
          if (isGitNotConfigured(e)) {
            return historyDiffViaBuilderJson(name, timestamp, builder);
          }
          throw e;
        }
      }
      return historyDiffViaBuilderJson(name, timestamp, builder);
    }
    var entry = historyService.readEntry(dir.path(), name, timestamp);
    if (entry.isEmpty()) {
      return historyEntryNotFound(name, timestamp);
    }
    String after = pretty(entry.get());
    var published = historyService.readPublished(dir.path(), name);
    String before = null;
    List<DiffHunk> hunks = List.of();
    boolean truncated = false;
    if (published.isPresent()) {
      before = pretty(published.get());
      LineDiff.Result result = LineDiff.diff(before, after, StarterConstants.DEFAULT_MAX_HUNKS,
              StarterConstants.LINE_DIFF_CONTEXT_LINES);
      hunks = result.hunks();
      truncated = result.truncated();
    }
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new HistoryDiffResponse(name, timestamp, before, after, hunks, truncated));
  }

  public ServerResponse restore(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    String timestamp = request.pathVariable("timestamp");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var builder = builderClient();
    if (builder != null) {
      var sourcePath = resolveSourcePath(name);
      if (sourcePath.isPresent() && isGitCommitId(timestamp)) {
        String path = sourcePath.get();
        try {
          String content = builder.vcsShow(path, timestamp);
          builder.stageWrite(path, content);
          builder.flushFiles();
          DraftResponse response = new DraftResponse(name, "Draft", path, false,
                  LoadResult.empty(), null, null, System.currentTimeMillis(), null);
          audit(request, ACTION_DRAFT_RESTORE, name, StarterConstants.OUTCOME_SUCCESS,
                  Map.of("location", path, "commitId", timestamp));
          log.info("[DSL drafts] restored {} (path={}) from commit {}", name, path, timestamp);
          return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (BuilderApiException e) {
          audit(request, ACTION_DRAFT_RESTORE, name, StarterConstants.OUTCOME_FAILURE,
                  Map.of("error", String.valueOf(e.getMessage())));
          if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return historyEntryNotFound(name, timestamp);
          }
          if (isGitNotConfigured(e)) {
            return restoreViaBuilderJson(name, timestamp, builder, dir.path());
          }
          throw e;
        } catch (RuntimeException e) {
          audit(request, ACTION_DRAFT_RESTORE, name, StarterConstants.OUTCOME_FAILURE,
                  Map.of("error", String.valueOf(e.getMessage())));
          if (isGitNotConfigured(e)) {
            return restoreViaBuilderJson(name, timestamp, builder, dir.path());
          }
          throw e;
        }
      }
      return restoreViaBuilderJson(name, timestamp, builder, dir.path());
    }
    var entry = historyService.readEntry(dir.path(), name, timestamp);
    if (entry.isEmpty()) {
      return historyEntryNotFound(name, timestamp);
    }
    historyService.snapshotBeforePublish(dir.path(), name);
    var payload = withStatus(entry.get(), "Published");
    Path file = writePayload(dir.path().resolve(WORKBENCH_PUBLISHED_DIR), payload);
    log.info("[DSL drafts] restored {} to published {} from history {}", name, file, timestamp);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(finishPublish(name, file.toString(), dir.path()));
  }

  public ServerResponse delete(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var builder = builderClient();
    if (builder != null) {
      DraftResponse deleted = builder.deleteDraft(name);
      log.info("[DSL drafts] deleted {} via DSL builder", name);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(deleted);
    }
    Path draftsDir = dir.path().resolve(WORKBENCH_DRAFTS_DIR);
    Path draftFile = draftsDir.resolve(safeFileName(name) + ".json").normalize();
    if (!draftFile.startsWith(draftsDir) || !Files.exists(draftFile)) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "Draft not found: " + name, name, null, null, null,
                      null, null, null));
    }
    Files.delete(draftFile);
    log.info("[DSL drafts] deleted {} from {}", name, draftFile);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new DraftResponse(name, "Deleted", null, false, LoadResult.empty(), null, null,
                    null, null));
  }

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    var dir = ensureConfigured(null);
    if (dir.isError()) {
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(new PageResponse<>(List.of(), 0L, skip, pageSize));
    }
    var builder = builderClient();
    if (builder != null) {
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(builder.listDrafts(pageSize, skip));
    }
    Path drafts = draftsDir(dir.path());
    if (!Files.isDirectory(drafts)) {
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(new PageResponse<>(List.of(), 0L, skip, pageSize));
    }
    List<DraftSummary> summaries = new ArrayList<>();
    try (var stream = Files.list(drafts)) {
      var files = stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".json"))
              .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
              .toList();
      for (Path file : files) {
        try {
          DraftRequest draft = objectMapper.readValue(file.toFile(), DraftRequest.class);
          long updatedAt = Files.getLastModifiedTime(file).toMillis();
          summaries.add(new DraftSummary(
                  draft.name(),
                  draft.type(),
                  draft.status(),
                  draft.version(),
                  updatedAt));
        } catch (Exception e) {
          log.warn("[DSL drafts] skipping unparseable draft file {}: {}", file,
                  e.getMessage());
        }
      }
    } catch (IOException e) {
      log.warn("[DSL drafts] failed to list drafts in {}: {}", drafts, e.getMessage());
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(new PageResponse<>(List.of(), 0L, skip, pageSize));
    }
    long total = summaries.size();
    List<DraftSummary> paged = summaries.stream()
            .skip(skip)
            .limit(pageSize)
            .toList();
    log.info("[DSL drafts] listed {} drafts from {} (total {})", paged.size(), drafts, total);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(paged, total, skip, pageSize));
  }

  public ServerResponse read(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var builder = builderClient();
    if (builder != null) {
      DraftRequest payload = builder.readDraft(name);
      log.info("[DSL drafts] read {} via DSL builder", name);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(payload);
    }
    Path drafts = draftsDir(dir.path());
    Path draftFile = drafts.resolve(safeFileName(name) + ".json").normalize();
    if (!draftFile.startsWith(drafts) || !Files.exists(draftFile)) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "Draft not found: " + name, name, null, null, null,
                      null, null, null));
    }
    DraftRequest payload = objectMapper.readValue(draftFile.toFile(), DraftRequest.class);
    log.info("[DSL drafts] read {} from {}", name, draftFile);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(payload);
  }

  public ServerResponse exportBundle(ServerRequest request) {
    var dir = ensureConfigured(null);
    if (dir.isError()) {
      return dir.response();
    }
    boolean includeDrafts = request.param("include").map("drafts"::equals).orElse(false);
    var builder = builderClient();
    if (builder != null) {
      DefinitionBundle bundle = builder.exportBundle(includeDrafts);
      log.info("[DSL bundle] exported {} definitions via DSL builder (includeDrafts={})",
              bundle.definitions().size(), includeDrafts);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(bundle);
    }
    DefinitionBundle bundle = bundleService.export(dir.path(), includeDrafts);
    log.info("[DSL bundle] exported {} definitions (includeDrafts={})",
            bundle.definitions().size(), includeDrafts);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(bundle);
  }

  public ServerResponse importBundle(ServerRequest request) throws IOException {
    var dir = ensureConfigured(null);
    if (dir.isError()) {
      return dir.response();
    }
    boolean dryRun = request.param("dryRun").map(Boolean::parseBoolean).orElse(false);

    DefinitionBundle bundle;
    try {
      String body = request.body(String.class);
      bundle = objectMapper.readValue(body, DefinitionBundle.class);
    } catch (JacksonException e) {
      log.warn("[DSL bundle] failed to parse bundle body: {}", e.getMessage());
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "malformed bundle JSON", null, null, null, null,
                      null, null, null));
    } catch (ServletException e) {
      throw new IOException("Failed to read bundle body", e);
    }

    try {
      bundleService.validateForImport(bundle);
      bundleService.verifyDigest(bundle);
    } catch (IllegalArgumentException e) {
      String raw = e.getMessage();
      String code = "BAD_REQUEST";
      String detail = raw;
      if (raw != null && raw.startsWith("BUNDLE_DIGEST_")) {
        int sep = raw.indexOf(':');
        if (sep > 0) {
          code = raw.substring(0, sep);
          detail = raw.substring(sep + 1).trim();
        } else {
          code = raw;
          detail = raw;
        }
      }
      log.warn("[DSL bundle] import validation failed: {}", e.getMessage());
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse(code, detail, null, null, null, null, null, null, null));
    }

    if (bundle.definitions().size() > BUNDLE_MAX_DEFINITIONS) {
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "bundle too large (max " + BUNDLE_MAX_DEFINITIONS
                      + " definitions)", null, null, null, null, null, null, null));
    }

    if (dryRun) {
      List<ImportEntryResult> results = bundleService.diffForImport(dir.path(), bundle);
      int published = (int) results.stream()
              .filter(r -> "created".equals(r.outcome()) || "updated".equals(r.outcome()))
              .count();
      int failed = (int) results.stream().filter(r -> "skipped".equals(r.outcome())).count();
      ImportBundleResult result = new ImportBundleResult(true, false, published, failed,
              results, null, null);
      log.info("[DSL bundle] dry-run import preview: {} created/updated, {} skipped",
              published, failed);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    var builder = builderClient();
    if (builder != null) {
      return importBundleViaBuilder(request, bundle, bundleTarget(bundle));
    }

    List<ImportEntryResult> results = new ArrayList<>();
    String bulkTarget = bundleTarget(bundle);
    try {
      for (DefinitionBundleEntry entry : bundle.definitions()) {
        DraftRequest payload = withStatus(entry.definition(), "Published");
        String name = payload.name();
        historyService.snapshotBeforePublish(dir.path(), name);
        Path file = writePayload(dir.path().resolve(WORKBENCH_PUBLISHED_DIR), payload);
        results.add(new ImportEntryResult(name, "published", null));
        log.info("[DSL bundle] imported published marker {} to {}", name, file);
      }
      audit(request, ACTION_DRAFT_BULK_WRITE, bulkTarget, StarterConstants.OUTCOME_SUCCESS,
              Map.of("count", results.size()));
    } catch (RuntimeException e) {
      audit(request, ACTION_DRAFT_BULK_WRITE, bulkTarget, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage()),
                      "succeeded", results.size(),
                      "attempted", bundle.definitions().size()));
      throw e;
    }

    ReloadOutcome outcome = reloadOutcome(bulkTarget);
    long publishedCount = results.stream().filter(r -> "published".equals(r.outcome())).count();
    long failedCount = results.size() - publishedCount;
    ImportBundleResult result = new ImportBundleResult(false, outcome.reloaded(),
            (int) publishedCount,
            (int) failedCount, results, outcome.error(), outcome.diagnostics());
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
  }

  private ServerResponse importBundleViaBuilder(ServerRequest request, DefinitionBundle bundle,
          String bulkTarget) throws IOException {
    ImportBundleResult imported = builderClient().importBundle(bundle, false);
    audit(request, ACTION_DRAFT_BULK_WRITE, bulkTarget, StarterConstants.OUTCOME_SUCCESS,
            Map.of("count", imported.results().size()));
    ReloadOutcome outcome = reloadOutcome(bulkTarget);
    ImportBundleResult result = new ImportBundleResult(false, outcome.reloaded(),
            imported.published(), imported.failed(), imported.results(), outcome.error(),
            outcome.diagnostics());
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
  }

  private ReloadOutcome reloadOutcome(String target) {
    try {
      LoadResult loadResult = reloadHandler.reloadDefinitions();
      log.info("[DSL bundle] import reloaded {} definitions: processes={}, transactions={},"
              + " functions={}",
              loadResult.total(), loadResult.processCount(), loadResult.transactionCount(),
              loadResult.functionCount());
      return new ReloadOutcome(true, null, null);
    } catch (Exception e) {
      log.warn("[DSL bundle] import wrote published markers but reload failed: {}", e.getMessage());
      var compilation = findDslCompilationException(e);
      if (compilation != null) {
        recordDiagnostics(CompileDiagnosticSource.PUBLISH, target, compilation.diagnostics());
        return new ReloadOutcome(false, compilation.getMessage(),
                compilation.diagnostics().stream().limit(20).toList());
      }
      if (e instanceof ValidationException ve) {
        return new ReloadOutcome(false, ve.getMessage(), toValidationDiagnostics(ve, target));
      }
      return new ReloadOutcome(false, e.getMessage(), null);
    }
  }

  private record ReloadOutcome(boolean reloaded, String error,
          List<CompileDiagnostic> diagnostics) {
  }

  private void audit(ServerRequest request, String action, String target, String outcome,
          Object details) {
    if (auditServiceProvider == null) {
      return;
    }
    var auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    auditService.record(DslAuditService.currentActor(), action, target,
            DslAuditService.correlationIdOf(request), outcome, details);
  }

  /**
   * Best-effort publish of a domain event for the draft lifecycle. There is NO surrounding DB
   * transaction for draft writes (the draft filesystem write is the state change itself, with no
   * row backing it), so unlike the run path the event insert does not need to be co-transactional.
   * A publish failure is logged-and-swallowed: the user's save/publish must succeed even if the
   * event store is briefly unavailable. See the loop note in docs/plans/T411.
   */
  private void publishEventBestEffort(@NonNull DomainEvent event) {
    var publisher = eventPublisherProvider == null
            ? null
            : eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    try {
      publisher.publish(event);
    } catch (RuntimeException e) {
      log.warn("[DSL events] best-effort publish of {} for aggregate {} failed: {}",
              event.eventType(), event.aggregateId(), e.getMessage());
    }
  }

  private @Nullable String correlationIdOf(@NonNull ServerRequest request) {
    try {
      return CorrelationId.validated(
              request.headers().firstHeader(StarterConstants.CORRELATION_ID_HEADER));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void recordDiagnostics(CompileDiagnosticSource source, String definition,
          List<CompileDiagnostic> diagnostics) {
    if (compileDiagnosticRepositoryProvider == null) {
      return;
    }
    var repository = compileDiagnosticRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return;
    }
    try {
      repository.insertAll(source, definition, diagnostics);
    } catch (RuntimeException e) {
      log.warn("[DSL diagnostics] failed to persist compile diagnostics from {} for {}: {}",
              source, definition, e.getMessage());
    }
  }

  private DslBuilderClient builderClient() {
    return builderClientProvider == null ? null : builderClientProvider.getIfAvailable();
  }

  /**
   * Caller role for the T568 approval gate: the same shared {@link RoleResolver} the RBAC filter
   * uses (falling back to the default claim when no bean is available, e.g. in bare test contexts).
   */
  private Role resolveRole(ServerRequest request) {
    RoleResolver resolver = roleResolverProvider == null
            ? null
            : roleResolverProvider.getIfAvailable();
    if (resolver == null) {
      resolver = new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME);
    }
    return resolver.resolve(request.servletRequest());
  }

  private static String bundleTarget(DefinitionBundle bundle) {
    String joined = bundle.definitions().stream()
            .map(e -> e.definition().name())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    return joined.length() > 400 ? joined.substring(0, 400) + "…" : joined;
  }

  private DraftResponse finishPublish(String name, String location, Path dir) throws IOException {
    boolean reloaded = false;
    LoadResult loadResult = LoadResult.empty();
    try {
      loadResult = reloadHandler.reloadDefinitions();
      reloaded = true;
      deleteDraftMarker(dir, name);
      log.info("[DSL drafts] publish of {} reloaded {} definitions: processes={}, transactions={},"
              + " functions={}",
              name, loadResult.total(), loadResult.processCount(), loadResult.transactionCount(),
              loadResult.functionCount());
    } catch (Exception e) {
      log.warn("[DSL drafts] publish of {} succeeded but reload failed: {}", name, e.getMessage());
      var compilation = findDslCompilationException(e);
      if (compilation != null) {
        recordDiagnostics(CompileDiagnosticSource.PUBLISH, name, compilation.diagnostics());
        return new DraftResponse(name, "Published", location, false,
                LoadResult.empty(),
                compilation.getMessage(), compilation.diagnostics().stream().limit(20).toList(),
                null, null);
      }
      if (e instanceof ValidationException ve) {
        return new DraftResponse(name, "Published", location, false, LoadResult.empty(),
                ve.getMessage(), toValidationDiagnostics(ve, name).stream().limit(20).toList(),
                null, null);
      }
      return new DraftResponse(name, "Published", location, false, LoadResult.empty(),
              e.getMessage(), null, null, null);
    }
    return new DraftResponse(name, "Published", location, reloaded, loadResult, null, null, null,
            null);
  }

  public ServerResponse discard(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var sourcePath = resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = builderClient();
    if (builder == null) {
      return error(HttpStatus.CONFLICT,
              new ErrorResponse(GIT_REQUIRES_BUILDER_CODE,
                      "Discard requires the DSL builder (git-backed workspace)", name,
                      null, null, null, null, null, null));
    }
    String path = sourcePath.get();
    try {
      builder.discard(new DiscardRequest(List.of(path)));
      try {
        builder.deleteDraft(name);
      } catch (cbs.nova.starter.exception.BuilderApiException e) {
        if (e.getStatusCode().value() != 404) {
          throw e;
        }
        log.debug("[DSL drafts] no legacy marker to delete for {} — ignored", name);
      }
      audit(request, ACTION_DRAFT_DISCARD, name, StarterConstants.OUTCOME_SUCCESS,
              Map.of("location", path));
      log.info("[DSL drafts] discarded {} via DSL builder (path={})", name, path);
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(new DraftResponse(name, "Discarded", path, false, LoadResult.empty(),
                      null, null, null, null));
    } catch (RuntimeException e) {
      audit(request, ACTION_DRAFT_DISCARD, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse commits(ServerRequest request) {
    String name = request.pathVariable("name");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var sourcePath = resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = builderClient();
    if (builder == null) {
      return error(HttpStatus.CONFLICT,
              new ErrorResponse(GIT_REQUIRES_BUILDER_CODE,
                      "Commit history requires the DSL builder (git-backed workspace)", name,
                      null, null, null, null, null, null));
    }
    int limit = parseLimit(request);
    String path = sourcePath.get();
    List<LogEntry> entries = builder.vcsLog(path, limit);
    log.info("[DSL drafts] listed {} commits for {} (path={})", entries.size(), name, path);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(entries);
  }

  private @Nullable String commitIfDirty(DslBuilderClient builder, ServerRequest request,
          String name) {
    var sourcePath = resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      log.debug("[DSL drafts] no source path for {} — skipping commit", name);
      return null;
    }
    String path = sourcePath.get();
    var status = gitStatusResolver().flatMap(r -> r.status(sourceDir()));
    boolean dirty = status.map(s -> DslGitStatusResolver.matchChange(s.changes(), path).isPresent())
            .orElse(false);
    if (!dirty) {
      log.debug("[DSL drafts] source path {} is clean — no commit needed", path);
      return null;
    }
    String actor = DslAuditService.currentActor();
    try {
      CommitResult result = builder.commit(new CommitRequest(List.of(path),
              "Publish " + name, actor, actor + "@cbs-nova.local"));
      log.info("[DSL drafts] committed {} (path={}) as {}", name, path, result.commitId());
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("commitId", String.valueOf(result.commitId()));
      details.put("location", path);
      details.put("message", "Publish " + name);
      if (result.pushed() != null) {
        details.put("pushed", result.pushed());
      }
      if (result.pushError() != null) {
        details.put("pushError", result.pushError());
      }
      audit(request, ACTION_DRAFT_COMMIT, name, StarterConstants.OUTCOME_SUCCESS, details);
      return result.commitId();
    } catch (RuntimeException e) {
      log.warn("[DSL drafts] commit for {} (path={}) failed: {}", name, path, e.getMessage());
      audit(request, ACTION_DRAFT_COMMIT, name, StarterConstants.OUTCOME_FAILURE,
              Map.of(COMMIT_ERROR_DETAIL, String.valueOf(e.getMessage()),
                      "location", path));
      return null;
    }
  }

  private ServerResponse historyViaBuilderJson(String name, DslBuilderClient builder) {
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(builder.history(name));
  }

  private ServerResponse historyEntryViaBuilderJson(String name, String timestamp,
          DslBuilderClient builder) {
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(builder.historyEntry(name, timestamp));
  }

  private ServerResponse historyDiffViaBuilderJson(String name, String timestamp,
          DslBuilderClient builder) {
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(builder.historyDiff(name, timestamp));
  }

  private ServerResponse restoreViaBuilderJson(String name, String timestamp,
          DslBuilderClient builder, Path dir) throws IOException {
    var restored = builder.restoreDraft(name, timestamp);
    log.info("[DSL drafts] restored {} via DSL builder from history {}", name, timestamp);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(finishPublish(name, restored.location(), dir));
  }

  private DraftRequest readDraftMetadataIgnoring404(DslBuilderClient builder, String name) {
    try {
      return builder.readDraft(name);
    } catch (BuilderApiException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return null;
      }
      throw e;
    }
  }

  private ServerResponse historyEntryNotFound(String name, String timestamp) {
    return error(HttpStatus.NOT_FOUND,
            new ErrorResponse("NOT_FOUND",
                    "No publish history entry " + timestamp + " for " + name, name, null, null,
                    null, null, null, null));
  }

  private boolean isGitCommitId(String timestamp) {
    if (timestamp == null
            || timestamp.matches(StarterConstants.WORKBENCH_HISTORY_TIMESTAMP_PATTERN)) {
      return false;
    }
    int len = timestamp.length();
    if (len < 7 || len > 40) {
      return false;
    }
    if (!timestamp.matches("^[0-9a-fA-F]+$")) {
      return false;
    }
    return len == 40 || timestamp.matches(".*[a-fA-F].*");
  }

  private boolean isGitNotConfigured(RuntimeException e) {
    return e instanceof BuilderApiException bae
            && bae.getStatusCode().value() == HttpStatus.CONFLICT.value()
            && GIT_NOT_CONFIGURED_CODE.equals(bae.getCode());
  }

  private Optional<String> resolveSourcePath(String name) {
    if (sourcePathResolverProvider == null) {
      return Optional.empty();
    }
    var resolver = sourcePathResolverProvider.getIfAvailable();
    if (resolver == null) {
      return Optional.empty();
    }
    return resolver.relativePath(name);
  }

  private Optional<DslGitStatusResolver> gitStatusResolver() {
    if (gitStatusResolverProvider == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(gitStatusResolverProvider.getIfAvailable());
  }

  private Path sourceDir() {
    String sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      throw new IllegalStateException("csb.dsl.source-dir is not configured");
    }
    return Path.of(sourceDirProperty);
  }

  private int parseLimit(ServerRequest request) {
    try {
      return request.param("limit")
              .map(Integer::parseInt)
              .map(v -> Math.max(1, Math.min(v, MAX_LOG_LIMIT)))
              .orElse(Integer.parseInt(DEFAULT_LOG_LIMIT));
    } catch (NumberFormatException e) {
      return Integer.parseInt(DEFAULT_LOG_LIMIT);
    }
  }

  private sealed interface PathResult {

    Path path();

    default boolean isError() {
      return false;
    }

    default ServerResponse response() {
      throw new IllegalStateException("not an error result");
    }

    record Ok(Path path) implements PathResult {
    }

    record Err(ServerResponse response) implements PathResult {

      @Override
      public Path path() {
        throw new IllegalStateException("not a path");
      }

      @Override
      public boolean isError() {
        return true;
      }
    }
  }

  private PathResult ensureConfigured(String name) {
    var sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_CONFIGURED", "csb.dsl.source-dir is not configured", name,
                      null, null, null, null, null, null)));
    }
    Path dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_FOUND", "Source directory does not exist: " + dir, name, null,
                      null, null, null, null, null)));
    }
    return new PathResult.Ok(dir);
  }

  private DraftRequest withSavedAt(DraftRequest body) {
    return new DraftRequest(body.name(), body.type(), body.status(), body.version(),
            body.taskQueue(), body.source(), System.currentTimeMillis());
  }

  private DraftRequest parse(ServerRequest request) throws IOException {
    try {
      return objectMapper.readValue(request.body(String.class), DraftRequest.class);
    } catch (JacksonException e) {
      log.warn("[DSL drafts] failed to parse request body: {}", e.getMessage(), e);
      return null;
    } catch (ServletException e) {
      throw new IOException("Failed to read request body", e);
    }
  }

  private static List<CompileDiagnostic> toValidationDiagnostics(ValidationException ex,
          String file) {
    return ex.issues().stream()
            .map(i -> new CompileDiagnostic(file, null, null, i.message(), "error", i.code()))
            .toList();
  }

  private static DslCompilationException findDslCompilationException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof DslCompilationException dce) {
        return dce;
      }
      current = current.getCause();
    }
    return null;
  }

  private String pretty(DraftRequest payload) throws IOException {
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
  }

  private DraftRequest withStatus(DraftRequest body, String status) {
    return new DraftRequest(
            body.name(),
            body.type(),
            status,
            body.version(),
            body.taskQueue(),
            body.source(),
            body.savedAt());
  }

  private Path writePayload(Path directory, DraftRequest payload) throws IOException {
    Files.createDirectories(directory);
    Path file = directory.resolve(safeFileName(payload.name()) + ".json");
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return file;
  }

  private static Path draftsDir(Path source) {
    return source.resolve(WORKBENCH_DRAFTS_DIR);
  }

  private void deleteDraftMarker(Path dir, String name) {
    try {
      Path draftFile = draftsDir(dir).resolve(safeFileName(name) + ".json").normalize();
      if (draftFile.startsWith(draftsDir(dir).normalize()) && Files.exists(draftFile)) {
        Files.delete(draftFile);
        log.info("[DSL drafts] deleted draft marker {} after publish", draftFile);
      }
    } catch (Exception e) {
      log.warn("[DSL drafts] failed to delete draft marker for {}: {}", name, e.getMessage());
    }
  }

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) {
    return ServerResponse.status(status).body(body);
  }

}

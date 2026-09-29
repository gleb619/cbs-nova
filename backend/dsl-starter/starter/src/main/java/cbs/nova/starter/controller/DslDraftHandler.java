package cbs.nova.starter.controller;

import java.util.Objects;

import jakarta.servlet.ServletException;
import tools.jackson.core.JacksonException;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.exception.ValidationException;
import cbs.nova.dsl.model.CompileDiagnostic;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.LoadResult;
import cbs.nova.dsl.vcs.RepoStatus;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.starter.model.CompileDiagnosticSource;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DraftDetail;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.VcsModels.DraftsMetadata;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import cbs.nova.starter.model.VcsModels.WorkspaceInfo;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionStatusResolver;
import cbs.nova.starter.service.DslGitStatusResolver;
import cbs.nova.starter.service.DslSourcePathResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "cbs.dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
@AllArgsConstructor
public class DslDraftHandler {

  static final String ACTION_DRAFT_WRITE = "DRAFT_WRITE";
  static final String ACTION_DEFINITION_PUBLISH = "DEFINITION_PUBLISH";
  static final String ACTION_DRAFT_BULK_WRITE = "DRAFT_BULK_WRITE";
  static final String ACTION_DRAFT_DISCARD = "DRAFT_DISCARD";
  static final String ACTION_DRAFT_COMMIT = "DEFINITION_PUBLISH_COMMIT";
  static final String COMMIT_ERROR_DETAIL = "commitError";

  private final DslProperties dslProperties;
  private final DslReloadHandler reloadHandler;
  private final ObjectMapper objectMapper;
  private final DslDefinitionBundleService bundleService;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;
  private final ObjectProvider<CompileDiagnosticRecordRepository> compileDiagnosticRepositoryProvider;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final ObjectProvider<RoleResolver> roleResolverProvider;
  private final ObjectProvider<DslSourcePathResolver> sourcePathResolverProvider;
  private final ObjectProvider<DslGitStatusResolver> gitStatusResolverProvider;
  private final ObjectProvider<DslDefinitionStatusResolver> statusResolverProvider;
  private final DslDraftSupport support;

  public ServerResponse save(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      support.audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "name is required"));
      return DslDraftSupport.error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null, null, null,
                      null, null));
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      support.audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "no source path"));
      return DslDraftSupport.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    if (body.source() == null || body.source().isBlank()) {
      support.audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "source is required"));
      return DslDraftSupport.error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "source is required", name, null, null, null,
                      null, null, null));
    }
    String path = sourcePath.get();
    try {
      support.writeSource(path, body.source());
      DraftResponse saved = new DraftResponse(name, "Draft", path, false, LoadResult.empty(),
              null, null, System.currentTimeMillis(), null);
      support.audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_SUCCESS,
              Map.of("location", path));
      support.publishEventBestEffort(new DomainEvent.DraftSaved(
              name, body.version(), body.taskQueue(), null, support.correlationIdOf(request)));
      log.info("[DSL drafts] saved {} to {}", name, path);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(saved);
    } catch (RuntimeException e) {
      support.audit(request, ACTION_DRAFT_WRITE, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse publish(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      support.audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "name is required"));
      return DslDraftSupport.error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null, null, null,
                      null, null));
    }
    if (dslProperties.approval().required() && !resolveRole(request).satisfies(Role.OPERATOR)) {
      support.audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "publish requires approval"));
      return DslDraftSupport.error(HttpStatus.FORBIDDEN,
              new ErrorResponse(StarterConstants.FORBIDDEN_CODE, "publish requires approval",
                      name, null, null, null, null, null, null));
    }
    return publishPayload(request, name, body);
  }

  /**
   * The publish flow (write source if supplied, reload, then commit if dirty and builder is
   * available) — shared by {@link #publish} and the T568 change-request approve path, which replays
   * an approved snapshot instead of the request body. Performs NO approval-gate check: gating is
   * the caller's responsibility.
   */
  public ServerResponse publishPayload(ServerRequest request, String name, DraftRequest body)
          throws IOException {
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      support.audit(request, ACTION_DEFINITION_PUBLISH, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "no source path"));
      return DslDraftSupport.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    String path = sourcePath.get();
    if (body != null && body.source() != null && !body.source().isBlank()) {
      support.writeSource(path, body.source());
      log.info("[DSL drafts] wrote supplied source for {} to {} before publishing", name, path);
    }
    DraftResponse response = finishPublish(name, path);
    boolean success = response.reloadError() == null;
    String commitId = null;
    if (success && response.reloaded()) {
      commitId = commitIfDirty(request, name);
    }
    DraftResponse withCommit = commitId == null
            ? response
            : new DraftResponse(response.name(), response.status(), response.location(),
                    response.reloaded(), response.loadResult(), response.reloadError(),
                    response.diagnostics(), response.savedAt(), commitId);
    support.audit(request, ACTION_DEFINITION_PUBLISH, name,
            success ? StarterConstants.OUTCOME_SUCCESS : StarterConstants.OUTCOME_FAILURE,
            Map.of("location", path,
                    "reloaded", withCommit.reloaded(),
                    "commitId", String.valueOf(commitId),
                    "error", success ? "" : String.valueOf(withCommit.reloadError())));
    support.publishEventBestEffort(new DomainEvent.DraftPublished(
            name, body != null ? body.version() : null,
            body != null ? body.taskQueue() : null,
            withCommit.reloaded(), withCommit.location(), null,
            support.correlationIdOf(request)));
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(withCommit);
  }

  public ServerResponse delete(ServerRequest request) throws IOException {
    return discard(request);
  }

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    var configured = support.ensureConfigured(null);
    if (configured.isError()) {
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(new PageResponse<>(List.of(), 0L, skip, pageSize));
    }

    List<DraftSummary> changed = allDefinitionNames().stream()
            .map(name -> draftSummaryIfChanged(name).orElse(null))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(DraftSummary::name))
            .toList();

    long total = changed.size();
    List<DraftSummary> page = changed.stream()
            .skip(skip)
            .limit(pageSize)
            .toList();
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(page, total, skip, pageSize));
  }

  public ServerResponse metadata(ServerRequest request) {
    WorkspaceInfo workspace = workspaceInfoOrNull();
    Path sourceRoot;
    String workspaceMode = null;
    if (workspace != null && workspace.workTree() != null && !workspace.workTree().isBlank()) {
      sourceRoot = Path.of(workspace.workTree());
      workspaceMode = workspace.mode();
    } else {
      sourceRoot = sourceDirOrNull();
    }

    String sourcePath = sourceRoot == null
            ? ""
            : sourceRoot.toString().replace('\\', '/');

    int draftCount = 0;
    Double sizeMb = null;
    if (sourceRoot != null && Files.isDirectory(sourceRoot)) {
      Optional<RepoStatus> status = gitStatusResolver()
              .flatMap(resolver -> resolver.status(sourceRoot));
      if (status.isPresent()) {
        List<String> changedPaths = changesUnderSourceRoot(status.get(), sourceRoot);
        draftCount = changedPaths.size();
        sizeMb = sizeMbOf(status.get(), sourceRoot, draftCount);
      }
    }

    boolean gitEnabled = dslProperties.git().enabled();
    int cacheTtl = dslProperties.git().statusCacheTtlSeconds();

    String gitBranch = null;
    if (gitEnabled) {
      try {
        DslBuilderClient client = support.builderClient();
        if (client != null) {
          gitBranch = client.vcsBranch();
        }
      } catch (RuntimeException e) {
        log.debug("[DSL drafts] metadata: branch lookup failed: {}", e.getMessage());
      }
    }

    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(new DraftsMetadata(draftCount, sourcePath, sizeMb, gitBranch,
                    gitEnabled, cacheTtl, workspaceMode));
  }

  /**
   * The builder is the source of truth for the workspace (mode + actual work tree). Best-effort:
   * any failure (builder down, older builder without the endpoint) yields {@code null} so the
   * caller falls back to the starter's configured {@code cbs.dsl.source-dir}.
   */
  private @Nullable WorkspaceInfo workspaceInfoOrNull() {
    DslBuilderClient client = support.builderClient();
    if (client == null) {
      return null;
    }
    try {
      return client.workspace();
    } catch (RuntimeException e) {
      log.debug("[DSL drafts] metadata: workspace lookup failed: {}", e.getMessage());
      return null;
    }
  }

  private @Nullable Path sourceDirOrNull() {
    String configured = dslProperties.sourceDir();
    if (configured == null || configured.isBlank()) {
      return null;
    }
    return Path.of(configured);
  }

  /**
   * Restrict a {@link RepoStatus} to paths under the workspace source root. The builder reports
   * repo-relative paths; the starter's draft count is only interested in DSL source files, so we
   * drop anything outside the source dir. The check resolves each path against the status's
   * {@code workTree} (the repo root) and keeps it only when the resolved absolute path lives inside
   * {@code sourceRoot}.
   */
  private static List<String> changesUnderSourceRoot(RepoStatus status, Path sourceRoot) {
    Path absoluteRoot = sourceRoot.toAbsolutePath().normalize();
    Path repoRoot = status.workTree() != null
            ? status.workTree().toAbsolutePath().normalize()
            : absoluteRoot;
    List<String> out = new ArrayList<>();
    for (String path : status.dirtyPaths()) {
      if (path == null || path.isBlank()) {
        continue;
      }
      Path resolved;
      try {
        resolved = repoRoot.resolve(path.replace('\\', '/')).normalize();
      } catch (RuntimeException e) {
        continue;
      }
      if (!resolved.startsWith(absoluteRoot)) {
        continue;
      }
      out.add(resolved.toString().replace('\\', '/'));
    }
    return out;
  }

  /**
   * Total size in MB of the changed source files, from the builder-reported
   * {@link RepoStatus#sizes} (repo-relative path → bytes). {@code 0.0} when there are no drafts;
   * {@code null} when the status carries no sizes (e.g. an older builder). Deleted files contribute
   * nothing.
   */
  private static @Nullable Double sizeMbOf(RepoStatus status, Path sourceRoot, int changedCount) {
    if (changedCount == 0) {
      return 0.0;
    }
    if (status.sizes().isEmpty()) {
      return null;
    }
    Path absoluteRoot = sourceRoot.toAbsolutePath().normalize();
    Path repoRoot = status.workTree() != null
            ? status.workTree().toAbsolutePath().normalize()
            : absoluteRoot;
    long totalBytes = status.sizes().entrySet().stream()
            .filter(e -> underSourceRoot(repoRoot, absoluteRoot, e.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
    return Math.round(totalBytes / 1_048_576.0 * 100.0) / 100.0;
  }

  private static boolean underSourceRoot(Path repoRoot, Path absoluteRoot,
          String repoRelativePath) {
    if (repoRelativePath == null || repoRelativePath.isBlank()) {
      return false;
    }
    try {
      return repoRoot.resolve(repoRelativePath.replace('\\', '/')).normalize()
              .startsWith(absoluteRoot);
    } catch (RuntimeException e) {
      return false;
    }
  }

  public ServerResponse read(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    var configured = support.ensureConfigured(name);
    if (configured.isError()) {
      return configured.response();
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return DslDraftSupport.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    String path = sourcePath.get();
    DefinitionStatus status = statusOf(name);
    DraftDetail payload = new DraftDetail(name, typeOf(name), status.value());
    log.info("[DSL drafts] read status for {} (path={})", name, path);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(payload);
  }

  public ServerResponse exportBundle(ServerRequest request) {
    var configured = support.ensureConfigured(null);
    if (configured.isError()) {
      return configured.response();
    }
    boolean includeDrafts = request.param("include").map("drafts"::equals).orElse(false);
    DefinitionBundle bundle = bundleService.export(configured.path(), includeDrafts);
    log.info("[DSL bundle] exported {} definitions (includeDrafts={})",
            bundle.definitions().size(), includeDrafts);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(bundle);
  }

  public ServerResponse importBundle(ServerRequest request) throws IOException {
    var configured = support.ensureConfigured(null);
    if (configured.isError()) {
      return configured.response();
    }
    boolean dryRun = request.param("dryRun").map(Boolean::parseBoolean).orElse(false);

    DefinitionBundle bundle;
    try {
      String body = request.body(String.class);
      bundle = objectMapper.readValue(body, DefinitionBundle.class);
    } catch (JacksonException e) {
      log.warn("[DSL bundle] failed to parse bundle: {}", e.getMessage());
      return DslDraftSupport.error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "malformed bundle JSON", null, null, null,
                      null, null, null, null));
    } catch (ServletException e) {
      throw new IOException("Failed to read request body", e);
    }

    try {
      bundleService.validateForImport(bundle);
      bundleService.verifyDigest(bundle);
    } catch (IllegalArgumentException e) {
      log.warn("[DSL bundle] validation failed: {}", e.getMessage());
      return DslDraftSupport.error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("BAD_REQUEST", e.getMessage(), null, null, null, null, null, null,
                      null));
    }

    if (bundle.definitions().size() > StarterConstants.BUNDLE_MAX_DEFINITIONS) {
      return DslDraftSupport.error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("BAD_REQUEST",
                      "Bundle exceeds " + StarterConstants.BUNDLE_MAX_DEFINITIONS
                              + " definitions",
                      null, null, null, null, null, null, null));
    }

    if (dryRun) {
      List<ImportEntryResult> results = bundleService.diffForImport(configured.path(), bundle);
      int wouldChange = (int) results.stream()
              .filter(r -> "created".equals(r.outcome()) || "updated".equals(r.outcome()))
              .count();
      int skipped = (int) results.stream().filter(r -> "skipped".equals(r.outcome())).count();
      ImportBundleResult result = new ImportBundleResult(true, false, wouldChange, skipped,
              results, null, null);
      log.info("[DSL bundle] dry-run import preview: {} created/updated, {} skipped",
              wouldChange, skipped);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    List<ImportEntryResult> results = bundleService.applyToTarget(configured.path(), bundle);
    support.flushFiles();

    List<String> writtenPaths = results.stream()
            .filter(r -> "published".equals(r.outcome()))
            .map(ImportEntryResult::name)
            .map(support::resolveSourcePath)
            .flatMap(Optional::stream)
            .toList();

    var builder = support.builderClient();
    boolean committed = false;
    if (builder != null && !writtenPaths.isEmpty()) {
      try {
        CommitResult commitResult = builder.commit(new CommitRequest(writtenPaths,
                "Import bundle", DslAuditService.currentActor(),
                DslAuditService.currentActor() + "@cbs-nova.local"));
        committed = commitResult.commitId() != null;
        log.info("[DSL bundle] committed {} paths as {}", writtenPaths.size(),
                commitResult.commitId());
      } catch (RuntimeException e) {
        log.warn("[DSL bundle] commit failed after import: {}", e.getMessage());
      }
    }

    ReloadOutcome outcome = reloadOutcome("bundle");
    int published = (int) results.stream()
            .filter(r -> "published".equals(r.outcome()) || "created".equals(r.outcome()))
            .count();
    int failed = results.size() - published;
    ImportBundleResult result = new ImportBundleResult(false, outcome.reloaded(),
            published, failed, results, outcome.error(), outcome.diagnostics());
    support.audit(request, ACTION_DRAFT_BULK_WRITE, bundleTarget(bundle),
            StarterConstants.OUTCOME_SUCCESS,
            Map.of("count", results.size(), "committed", committed));
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
  }

  public ServerResponse discard(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    var configured = support.ensureConfigured(name);
    if (configured.isError()) {
      return configured.response();
    }
    var sourcePath = support.resolveSourcePath(name);
    if (sourcePath.isEmpty()) {
      return DslDraftSupport.error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "No source path for definition: " + name, name,
                      null, null, null, null, null, null));
    }
    var builder = support.builderClient();
    if (builder == null) {
      return support.gitRequiresBuilder();
    }
    String path = sourcePath.get();
    try {
      builder.discard(new DiscardRequest(List.of(path)));
      support.audit(request, ACTION_DRAFT_DISCARD, name, StarterConstants.OUTCOME_SUCCESS,
              Map.of("location", path));
      log.info("[DSL drafts] discarded {} (path={})", name, path);
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(new DraftResponse(name, "Discarded", path, false, LoadResult.empty(),
                      null, null, null, null));
    } catch (RuntimeException e) {
      support.audit(request, ACTION_DRAFT_DISCARD, name, StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  private @Nullable String commitIfDirty(ServerRequest request, String name) {
    var sourcePath = support.resolveSourcePath(name);
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
    var builder = support.builderClient();
    if (builder == null) {
      log.debug("[DSL drafts] no builder for {} — skipping commit", name);
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
      support.audit(request, ACTION_DRAFT_COMMIT, name, StarterConstants.OUTCOME_SUCCESS, details);
      return result.commitId();
    } catch (RuntimeException e) {
      log.warn("[DSL drafts] commit for {} (path={}) failed: {}", name, path, e.getMessage());
      support.audit(request, ACTION_DRAFT_COMMIT, name, StarterConstants.OUTCOME_FAILURE,
              Map.of(COMMIT_ERROR_DETAIL, String.valueOf(e.getMessage()),
                      "location", path));
      return null;
    }
  }

  private DraftResponse finishPublish(String name, String location) throws IOException {
    boolean reloaded = false;
    LoadResult loadResult = LoadResult.empty();
    try {
      loadResult = reloadHandler.reloadDefinitions();
      reloaded = true;
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

  private Optional<DraftSummary> draftSummaryIfChanged(String name) {
    Optional<String> path = support.resolveSourcePath(name);
    if (path.isEmpty()) {
      return Optional.empty();
    }
    DefinitionStatus status = statusOf(name);
    if (status == DefinitionStatus.PUBLISHED) {
      return Optional.empty();
    }
    return Optional.of(new DraftSummary(
            name,
            typeOf(name),
            status.value(),
            null,
            0L));
  }

  private DefinitionStatus statusOf(String name) {
    var resolver = statusResolverProvider == null ? null : statusResolverProvider.getIfAvailable();
    if (resolver == null) {
      return DefinitionStatus.PUBLISHED;
    }
    return resolver.resolve(name);
  }

  private String typeOf(String name) {
    GlobalManager gm = GlobalManager.globalManager();
    if (gm.findProcess(name) != null) {
      return "process";
    }
    if (gm.findTransaction(name) != null) {
      return "transaction";
    }
    if (gm.findFunction(name) != null) {
      return "function";
    }
    return null;
  }

  private List<String> allDefinitionNames() {
    GlobalManager gm = GlobalManager.globalManager();
    return Stream.concat(
            gm.processNames().stream(),
            Stream.concat(gm.transactionNames().stream(), gm.helperNames().stream()))
            .distinct()
            .toList();
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

  private Optional<DslGitStatusResolver> gitStatusResolver() {
    if (gitStatusResolverProvider == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(gitStatusResolverProvider.getIfAvailable());
  }

  private Path sourceDir() {
    String sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      throw new IllegalStateException("cbs.dsl.source-dir is not configured");
    }
    return Path.of(sourceDirProperty);
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

  private static String bundleTarget(DefinitionBundle bundle) {
    String joined = bundle.definitions().stream()
            .map(e -> e.definition().name())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    return joined.length() > 400 ? joined.substring(0, 400) + "…" : joined;
  }

  private record ReloadOutcome(boolean reloaded, String error,
          List<CompileDiagnostic> diagnostics) {
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
      log.warn("[DSL bundle] import wrote files but reload failed: {}", e.getMessage());
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
}

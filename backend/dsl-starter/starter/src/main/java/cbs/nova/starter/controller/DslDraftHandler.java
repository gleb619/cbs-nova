package cbs.nova.starter.controller;

import cbs.nova.dsl.LoadResult;
import cbs.nova.dsl.ValidationException;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.starter.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.VcsModels.HistoryDiffResponse;
import cbs.nova.starter.model.VcsModels.DiffHunk;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import cbs.nova.starter.model.VcsModels.ImportEntryResult;
import cbs.nova.starter.model.CompileDiagnostic;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.controller.Pagination;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionBundleService;
import cbs.nova.starter.service.DslDefinitionHistoryService;
import cbs.nova.starter.util.LineDiff;
import tools.jackson.core.JacksonException;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "csb.dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslDraftHandler {

  private static final String DRAFTS_DIR = ".workbench/drafts";
  private static final String PUBLISHED_DIR = ".workbench/published";
  private static final int BUNDLE_MAX_DEFINITIONS = 200;

  static final String ACTION_DRAFT_WRITE = "DRAFT_WRITE";
  static final String ACTION_DEFINITION_PUBLISH = "DEFINITION_PUBLISH";
  static final String ACTION_DRAFT_BULK_WRITE = "DRAFT_BULK_WRITE";

  private final DslProperties dslProperties;
  private final DslReloadHandler reloadHandler;
  private final DslDefinitionHistoryService historyService;
  private final ObjectMapper objectMapper;
  private final DslDefinitionBundleService bundleService;
  private final ObjectProvider<DslAuditService> auditServiceProvider;

  public DslDraftHandler(DslProperties dslProperties, DslReloadHandler reloadHandler,
          DslDefinitionHistoryService historyService, ObjectMapper objectMapper,
          DslDefinitionBundleService bundleService) {
    this(dslProperties, reloadHandler, historyService, objectMapper, bundleService, null);
  }

  @Autowired
  public DslDraftHandler(DslProperties dslProperties, DslReloadHandler reloadHandler,
          DslDefinitionHistoryService historyService, ObjectMapper objectMapper,
          DslDefinitionBundleService bundleService,
          ObjectProvider<DslAuditService> auditServiceProvider) {
    this.dslProperties = dslProperties;
    this.reloadHandler = reloadHandler;
    this.historyService = historyService;
    this.objectMapper = objectMapper;
    this.bundleService = bundleService;
    this.auditServiceProvider = auditServiceProvider;
  }

  public ServerResponse save(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      audit(request, ACTION_DRAFT_WRITE, name, DslAuditService.OUTCOME_FAILURE,
              Map.of("error", "name is required"));
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null));
    }
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      audit(request, ACTION_DRAFT_WRITE, name, DslAuditService.OUTCOME_FAILURE,
              Map.of("error", "drafts directory not configured"));
      return dir.response();
    }
    var payload = withStatus(body, "Draft");
    try {
      Path file = writePayload(dir.path().resolve(DRAFTS_DIR), payload);
      audit(request, ACTION_DRAFT_WRITE, name, DslAuditService.OUTCOME_SUCCESS,
              Map.of("location", file.toString()));
      log.info("[DSL drafts] saved {} to {}", name, file);
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(new DraftResponse(name, "Draft", file.toString(), false, LoadResult.empty()));
    } catch (IOException | RuntimeException e) {
      audit(request, ACTION_DRAFT_WRITE, name, DslAuditService.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      throw e;
    }
  }

  public ServerResponse publish(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, DslAuditService.OUTCOME_FAILURE,
              Map.of("error", "name is required"));
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null));
    }
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, DslAuditService.OUTCOME_FAILURE,
              Map.of("error", "drafts directory not configured"));
      return dir.response();
    }
    var payload = withStatus(body, "Published");
    try {
      historyService.snapshotBeforePublish(dir.path(), name);
      Path file = writePayload(dir.path().resolve(PUBLISHED_DIR), payload);
      log.info("[DSL drafts] published {} to {}", name, file);
      DraftResponse response = finishPublish(name, dir.path(), file);
      boolean success = response.reloadError() == null;
      audit(request, ACTION_DEFINITION_PUBLISH, name,
              success ? DslAuditService.OUTCOME_SUCCESS : DslAuditService.OUTCOME_FAILURE,
              Map.of("location", file.toString(),
                      "reloaded", response.reloaded(),
                      "error", success ? "" : String.valueOf(response.reloadError())));
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(response);
    } catch (IOException | RuntimeException e) {
      audit(request, ACTION_DEFINITION_PUBLISH, name, DslAuditService.OUTCOME_FAILURE,
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
    var entry = historyService.readEntry(dir.path(), name, timestamp);
    if (entry.isEmpty()) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND",
                      "No publish history entry " + timestamp + " for " + name,
                      name, null, null));
    }
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(entry.get());
  }

  public ServerResponse historyDiff(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    String timestamp = request.pathVariable("timestamp");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var entry = historyService.readEntry(dir.path(), name, timestamp);
    if (entry.isEmpty()) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND",
                      "No publish history entry " + timestamp + " for " + name,
                      name, null, null));
    }
    String after = pretty(entry.get());
    var published = historyService.readPublished(dir.path(), name);
    String before = null;
    List<DiffHunk> hunks = List.of();
    boolean truncated = false;
    if (published.isPresent()) {
      before = pretty(published.get());
      LineDiff.Result result = LineDiff.diff(before, after);
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
    var entry = historyService.readEntry(dir.path(), name, timestamp);
    if (entry.isEmpty()) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND",
                      "No publish history entry " + timestamp + " for " + name,
                      name, null, null));
    }
    historyService.snapshotBeforePublish(dir.path(), name);
    var payload = withStatus(entry.get(), "Published");
    Path file = writePayload(dir.path().resolve(PUBLISHED_DIR), payload);
    log.info("[DSL drafts] restored {} to published {} from history {}", name, file, timestamp);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(finishPublish(name, dir.path(), file));
  }

  public ServerResponse delete(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    Path draftsDir = dir.path().resolve(DRAFTS_DIR);
    Path draftFile = draftsDir.resolve(safeFileName(name) + ".json").normalize();
    if (!draftFile.startsWith(draftsDir) || !Files.exists(draftFile)) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "Draft not found: " + name, name, null, null));
    }
    Files.delete(draftFile);
    log.info("[DSL drafts] deleted {} from {}", name, draftFile);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new DraftResponse(name, "Deleted", null, false, LoadResult.empty()));
  }

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", Pagination.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", Pagination.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    var dir = ensureConfigured(null);
    if (dir.isError()) {
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
              .body(new PageResponse<>(List.of(), 0L, skip, pageSize));
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
    Path drafts = draftsDir(dir.path());
    Path draftFile = drafts.resolve(safeFileName(name) + ".json").normalize();
    if (!draftFile.startsWith(drafts) || !Files.exists(draftFile)) {
      return error(HttpStatus.NOT_FOUND,
              new ErrorResponse("NOT_FOUND", "Draft not found: " + name, name, null, null));
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
              new ErrorResponse("INVALID_REQUEST", "malformed bundle JSON", null, null, null));
    } catch (ServletException e) {
      throw new IOException("Failed to read bundle body", e);
    }

    try {
      bundleService.validateForImport(bundle);
    } catch (IllegalArgumentException e) {
      log.warn("[DSL bundle] import validation failed: {}", e.getMessage());
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("BAD_REQUEST", e.getMessage(), null, null, null));
    }

    if (bundle.definitions().size() > BUNDLE_MAX_DEFINITIONS) {
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "bundle too large (max " + BUNDLE_MAX_DEFINITIONS
                      + " definitions)", null, null, null));
    }

    if (dryRun) {
      List<ImportEntryResult> results = bundle.definitions().stream()
              .map(e -> new ImportEntryResult(e.definition().name(), "skipped", "dry run"))
              .toList();
      ImportBundleResult result = new ImportBundleResult(true, false, bundle.definitions().size(),
              0,
              results, null, null);
      log.info("[DSL bundle] dry-run import of {} definitions", bundle.definitions().size());
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    List<ImportEntryResult> results = new ArrayList<>();
    String bulkTarget = bundleTarget(bundle);
    try {
      for (DefinitionBundleEntry entry : bundle.definitions()) {
        DraftRequest payload = withStatus(entry.definition(), "Published");
        String name = payload.name();
        historyService.snapshotBeforePublish(dir.path(), name);
        Path file = writePayload(dir.path().resolve(PUBLISHED_DIR), payload);
        results.add(new ImportEntryResult(name, "published", null));
        log.info("[DSL bundle] imported published marker {} to {}", name, file);
      }
      audit(request, ACTION_DRAFT_BULK_WRITE, bulkTarget, DslAuditService.OUTCOME_SUCCESS,
              Map.of("count", results.size()));
    } catch (RuntimeException e) {
      audit(request, ACTION_DRAFT_BULK_WRITE, bulkTarget, DslAuditService.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage()),
                      "succeeded", results.size(),
                      "attempted", bundle.definitions().size()));
      throw e;
    }

    boolean reloaded = false;
    String reloadError = null;
    List<CompileDiagnostic> diagnostics = null;
    try {
      LoadResult loadResult = reloadHandler.reloadDefinitions();
      reloaded = true;
      log.info("[DSL bundle] import reloaded {} definitions: processes={}, transactions={},"
              + " functions={}",
              loadResult.total(), loadResult.processCount(), loadResult.transactionCount(),
              loadResult.functionCount());
    } catch (Exception e) {
      log.warn("[DSL bundle] import wrote published markers but reload failed: {}", e.getMessage());
      var compilation = findDslCompilationException(e);
      if (compilation != null) {
        reloadError = compilation.getMessage();
        diagnostics = compilation.diagnostics();
      } else if (e instanceof ValidationException ve) {
        reloadError = ve.getMessage();
        diagnostics = toValidationDiagnostics(ve, bulkTarget);
      } else {
        reloadError = e.getMessage();
      }
    }

    long publishedCount = results.stream().filter(r -> "published".equals(r.outcome())).count();
    long failedCount = results.size() - publishedCount;
    ImportBundleResult result = new ImportBundleResult(false, reloaded, (int) publishedCount,
            (int) failedCount, results, reloadError, diagnostics);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
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

  private static String bundleTarget(DefinitionBundle bundle) {
    String joined = bundle.definitions().stream()
            .map(e -> e.definition().name())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    return joined.length() > 400 ? joined.substring(0, 400) + "…" : joined;
  }

  private DraftResponse finishPublish(String name, Path dir, Path file) throws IOException {
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
        return new DraftResponse(name, "Published", file.toString(), false,
                LoadResult.empty(),
                compilation.getMessage(), compilation.diagnostics());
      }
      if (e instanceof ValidationException ve) {
        return new DraftResponse(name, "Published", file.toString(), false, LoadResult.empty(),
                ve.getMessage(), toValidationDiagnostics(ve, name));
      }
      return new DraftResponse(name, "Published", file.toString(), false, LoadResult.empty(),
              e.getMessage(), null);
    }
    return new DraftResponse(name, "Published", file.toString(), reloaded, loadResult);
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
                      null,
                      null)));
    }
    Path dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_FOUND", "Source directory does not exist: " + dir, name, null,
                      null)));
    }
    return new PathResult.Ok(dir);
  }

  private DraftRequest parse(ServerRequest request) throws IOException {
    try {
      return objectMapper.readValue(request.body(InputStream.class), DraftRequest.class);
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
            body.taskQueue());
  }

  private Path writePayload(Path directory, DraftRequest payload) throws IOException {
    Files.createDirectories(directory);
    Path file = directory.resolve(safeFileName(payload.name()) + ".json");
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return file;
  }

  private static Path draftsDir(Path source) {
    return source.resolve(DRAFTS_DIR);
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

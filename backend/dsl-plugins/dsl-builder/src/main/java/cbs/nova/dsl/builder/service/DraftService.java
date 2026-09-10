package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.LoadResult;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.PageResponse;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionBundle;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionBundleEntry;
import cbs.nova.dsl.builder.model.VcsModels.DefinitionHistoryEntry;
import cbs.nova.dsl.builder.model.VcsModels.DiffHunk;
import cbs.nova.dsl.builder.model.VcsModels.DraftRequest;
import cbs.nova.dsl.builder.model.VcsModels.DraftResponse;
import cbs.nova.dsl.builder.model.VcsModels.DraftSummary;
import cbs.nova.dsl.builder.model.VcsModels.HistoryDiffResponse;
import cbs.nova.dsl.builder.model.VcsModels.ImportBundleResult;
import cbs.nova.dsl.builder.model.VcsModels.ImportEntryResult;
import cbs.nova.dsl.builder.util.LineDiff;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftService {

  // TODO: replace hardcode with app.yml settings
  private static final String DRAFTS_DIR = ".workbench/drafts";
  private static final String PUBLISHED_DIR = ".workbench/published";
  private static final int BUNDLE_MAX_DEFINITIONS = 200;
  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 500;

  private final DslBuilderProperties properties;
  private final ObjectMapper objectMapper;
  private final DefinitionHistoryService historyService;
  private final DefinitionBundleService bundleService;

  public DraftResponse save(String name, DraftRequest body) throws IOException {
    requireName(name, body);
    Path dir = workspaceRoot();
    DraftRequest payload = withStatus(body, "Draft");
    Path file = writePayload(dir.resolve(DRAFTS_DIR), payload);
    log.info("[DSL drafts] saved {} to {}", name, file);
    return new DraftResponse(name, "Draft", file.toString(), false, LoadResult.empty());
  }

  public DraftResponse publish(String name, DraftRequest body) throws IOException {
    requireName(name, body);
    Path dir = workspaceRoot();
    DraftRequest payload = withStatus(body, "Published");
    historyService.snapshotBeforePublish(dir, name);
    Path file = writePayload(dir.resolve(PUBLISHED_DIR), payload);
    deleteDraftMarker(dir, name);
    log.info("[DSL drafts] published {} to {}", name, file);
    return new DraftResponse(name, "Published", file.toString(), false, LoadResult.empty());
  }

  public List<DefinitionHistoryEntry> history(String name) {
    Path dir = workspaceRoot();
    return historyService.list(dir, name);
  }

  public DraftRequest historyEntry(String name, String timestamp) {
    Path dir = workspaceRoot();
    return historyService.readEntry(dir, name, timestamp)
            .orElseThrow(() -> notFound(
                    "No publish history entry " + timestamp + " for " + name));
  }

  public HistoryDiffResponse historyDiff(String name, String timestamp) throws IOException {
    Path dir = workspaceRoot();
    DraftRequest entry = historyService.readEntry(dir, name, timestamp)
            .orElseThrow(() -> notFound(
                    "No publish history entry " + timestamp + " for " + name));
    String after = pretty(entry);
    var published = historyService.readPublished(dir, name);
    String before = null;
    List<DiffHunk> hunks = List.of();
    boolean truncated = false;
    if (published.isPresent()) {
      before = pretty(published.get());
      LineDiff.Result result = LineDiff.diff(before, after);
      hunks = result.hunks();
      truncated = result.truncated();
    }
    return new HistoryDiffResponse(name, timestamp, before, after, hunks, truncated);
  }

  public DraftResponse restore(String name, String timestamp) throws IOException {
    Path dir = workspaceRoot();
    DraftRequest entry = historyService.readEntry(dir, name, timestamp)
            .orElseThrow(() -> notFound(
                    "No publish history entry " + timestamp + " for " + name));
    historyService.snapshotBeforePublish(dir, name);
    DraftRequest payload = withStatus(entry, "Published");
    Path file = writePayload(dir.resolve(PUBLISHED_DIR), payload);
    log.info("[DSL drafts] restored {} to published {} from history {}", name, file, timestamp);
    return new DraftResponse(name, "Published", file.toString(), false, LoadResult.empty());
  }

  public DraftResponse delete(String name) throws IOException {
    Path dir = workspaceRoot();
    Path draftsDir = dir.resolve(DRAFTS_DIR);
    Path draftFile = draftsDir.resolve(safeFileName(name) + ".json").normalize();
    if (!draftFile.startsWith(draftsDir) || !Files.exists(draftFile)) {
      throw notFound("Draft not found: " + name);
    }
    Files.delete(draftFile);
    log.info("[DSL drafts] deleted {} from {}", name, draftFile);
    return new DraftResponse(name, "Deleted", null, false, LoadResult.empty());
  }

  public PageResponse<DraftSummary> list(Integer limit, Integer offset) {
    int pageSize = clampLimit(limit == null ? DEFAULT_LIMIT : limit);
    int skip = Math.max(0, offset == null ? 0 : offset);
    Path root = properties.workspaceDir();
    if (root == null) {
      return new PageResponse<>(List.of(), 0L, skip, pageSize);
    }
    Path drafts = root.resolve(DRAFTS_DIR);
    if (!Files.isDirectory(drafts)) {
      return new PageResponse<>(List.of(), 0L, skip, pageSize);
    }
    List<DraftSummary> summaries = readSummaries(drafts);
    long total = summaries.size();
    List<DraftSummary> paged = summaries.stream()
            .skip(skip)
            .limit(pageSize)
            .toList();
    log.info("[DSL drafts] listed {} drafts from {} (total {})", paged.size(), drafts, total);
    return new PageResponse<>(paged, total, skip, pageSize);
  }

  public DraftRequest read(String name) throws IOException {
    Path dir = workspaceRoot();
    Path drafts = dir.resolve(DRAFTS_DIR);
    Path draftFile = drafts.resolve(safeFileName(name) + ".json").normalize();
    if (!draftFile.startsWith(drafts) || !Files.exists(draftFile)) {
      throw notFound("Draft not found: " + name);
    }
    DraftRequest payload = objectMapper.readValue(draftFile.toFile(), DraftRequest.class);
    log.info("[DSL drafts] read {} from {}", name, draftFile);
    return payload;
  }

  public DefinitionBundle exportBundle(boolean includeDrafts) {
    Path dir = workspaceRoot();
    DefinitionBundle bundle = bundleService.export(dir, includeDrafts);
    log.info("[DSL bundle] exported {} definitions (includeDrafts={})",
            bundle.definitions().size(), includeDrafts);
    return bundle;
  }

  public ImportBundleResult importBundle(DefinitionBundle bundle, boolean dryRun)
          throws IOException {
    Path dir = workspaceRoot();
    bundleService.validateForImport(bundle);
    if (bundle.definitions().size() > BUNDLE_MAX_DEFINITIONS) {
      throw new BuilderApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
              "bundle too large (max " + BUNDLE_MAX_DEFINITIONS + " definitions)");
    }
    if (dryRun) {
      List<ImportEntryResult> results = bundle.definitions().stream()
              .map(e -> new ImportEntryResult(e.definition().name(), "skipped", "dry run"))
              .toList();
      log.info("[DSL bundle] dry-run import of {} definitions", bundle.definitions().size());
      return new ImportBundleResult(true, false, bundle.definitions().size(), 0,
              results, null, null);
    }
    List<ImportEntryResult> results = new ArrayList<>();
    for (DefinitionBundleEntry entry : bundle.definitions()) {
      DraftRequest payload = withStatus(entry.definition(), "Published");
      String name = payload.name();
      historyService.snapshotBeforePublish(dir, name);
      Path file = writePayload(dir.resolve(PUBLISHED_DIR), payload);
      results.add(new ImportEntryResult(name, "published", null));
      log.info("[DSL bundle] imported published marker {} to {}", name, file);
    }
    long publishedCount = results.stream().filter(r -> "published".equals(r.outcome())).count();
    return new ImportBundleResult(false, false, (int) publishedCount,
            results.size() - (int) publishedCount, results, null, null);
  }

  private List<DraftSummary> readSummaries(Path drafts) {
    List<DraftSummary> summaries = new ArrayList<>();
    try (Stream<Path> stream = Files.list(drafts)) {
      for (Path file : stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".json"))
              .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
              .toList()) {
        try {
          DraftRequest draft = objectMapper.readValue(file.toFile(), DraftRequest.class);
          long updatedAt = Files.getLastModifiedTime(file).toMillis();
          summaries.add(new DraftSummary(
                  draft.name(), draft.type(), draft.status(), draft.version(), updatedAt));
        } catch (Exception e) {
          log.warn("[DSL drafts] skipping unparseable draft file {}: {}", file, e.getMessage());
        }
      }
    } catch (IOException e) {
      log.warn("[DSL drafts] failed to list drafts in {}: {}", drafts, e.getMessage());
    }
    return summaries;
  }

  private void requireName(String name, DraftRequest body) {
    if (body == null || body.name() == null || body.name().isBlank()) {
      throw new BuilderApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
              "name is required");
    }
  }

  private Path workspaceRoot() {
    Path dir = properties.workspaceDir();
    if (dir == null) {
      throw new BuilderApiException(HttpStatus.CONFLICT, "NOT_CONFIGURED",
              "cbs.dsl.builder.workspace-dir is not configured");
    }
    return dir;
  }

  private BuilderApiException notFound(String message) {
    return new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }

  private int clampLimit(int limit) {
    return Math.max(1, Math.min(limit, MAX_LIMIT));
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

  private void deleteDraftMarker(Path dir, String name) {
    try {
      Path draftFile = dir.resolve(DRAFTS_DIR).resolve(safeFileName(name) + ".json").normalize();
      if (draftFile.startsWith(dir.resolve(DRAFTS_DIR).normalize()) && Files.exists(draftFile)) {
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

}

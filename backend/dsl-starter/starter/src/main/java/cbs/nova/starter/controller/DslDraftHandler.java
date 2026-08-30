package cbs.nova.starter.controller;

import cbs.nova.dsl.LoadResult;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import cbs.nova.starter.model.VcsModels.DraftResponse;
import cbs.nova.starter.model.VcsModels.DraftSummary;
import cbs.nova.starter.model.ErrorResponse;
import tools.jackson.core.JacksonException;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DslDraftHandler {

  private static final String DRAFTS_DIR = ".workbench/drafts";
  private static final String PUBLISHED_DIR = ".workbench/published";

  private final DslProperties dslProperties;
  private final DslReloadHandler reloadHandler;
  private final ObjectMapper objectMapper;

  public ServerResponse save(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null));
    }
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var payload = withStatus(body, "Draft");
    Path file = writePayload(dir.path().resolve(DRAFTS_DIR), payload);
    log.info("[DSL drafts] saved {} to {}", name, file);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new DraftResponse(name, "Draft", file.toString(), false, LoadResult.empty()));
  }

  public ServerResponse publish(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    DraftRequest body = parse(request);
    if (body == null || body.name() == null || body.name().isBlank()) {
      return error(HttpStatus.BAD_REQUEST,
              new ErrorResponse("INVALID_REQUEST", "name is required", name, null, null));
    }
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    var payload = withStatus(body, "Published");
    Path file = writePayload(dir.path().resolve(PUBLISHED_DIR), payload);
    log.info("[DSL drafts] published {} to {}", name, file);

    // Reload the DSL registry and surface the drilldown of what got loaded. A reload failure
    // (e.g. compile error) must not fail the publish itself — the draft is already persisted.
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
    }
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new DraftResponse(name, "Published", file.toString(), reloaded, loadResult));
  }

  public ServerResponse delete(ServerRequest request) throws IOException {
    String name = request.pathVariable("name");
    var dir = ensureConfigured(name);
    if (dir.isError()) {
      return dir.response();
    }
    Path draftsDir = dir.path().resolve(DRAFTS_DIR);
    Path draftFile = draftsDir.resolve(safeFileName(name) + ".json");
    if (!Files.exists(draftFile)) {
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
    var dir = ensureConfigured(null);
    if (dir.isError()) {
      // Unconfigured source-dir: an empty list is a valid answer (matches the
      // frontend expectation that "no drafts configured" is not an error).
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(List.of());
    }
    Path drafts = draftsDir(dir.path());
    if (!Files.isDirectory(drafts)) {
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(List.of());
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
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(List.of());
    }
    log.info("[DSL drafts] listed {} drafts from {}", summaries.size(), drafts);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(summaries);
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
    var sourceDirProperty = dslProperties.getSourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_CONFIGURED", "dsl.source-dir is not configured", name, null,
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

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) {
    return ServerResponse.status(status).body(body);
  }
}

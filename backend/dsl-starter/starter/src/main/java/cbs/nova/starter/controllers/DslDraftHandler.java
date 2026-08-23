package cbs.nova.starter.controllers;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.models.DraftRequest;
import cbs.nova.starter.models.DraftResponse;
import cbs.nova.starter.models.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
@ConditionalOnProperty(prefix = "dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DslDraftHandler {

  private static final String DRAFTS_DIR = ".workbench/drafts";
  private static final String PUBLISHED_DIR = ".workbench/published";

  private final DslProperties dslProperties;
  private final DslReloadHandler reloadHandler;
  private final ObjectMapper objectMapper;

  public DslDraftHandler(DslProperties dslProperties,
          DslReloadHandler reloadHandler,
          ObjectMapper objectMapper) {
    this.dslProperties = dslProperties;
    this.reloadHandler = reloadHandler;
    this.objectMapper = objectMapper;
  }

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
            .body(new DraftResponse(name, "Draft", file.toString(), false));
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
    ServerResponse reload = reloadHandler.reload(request);
    boolean reloaded = reload.statusCode().is2xxSuccessful();
    if (!reloaded) {
      log.warn("[DSL drafts] publish of {} succeeded but reload returned {}",
              name, reload.statusCode().value());
    }
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new DraftResponse(name, "Published", file.toString(), reloaded));
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
              new ErrorResponse("NOT_CONFIGURED", "dsl.source-dir is not configured", name, null, null)));
    }
    Path dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_FOUND", "Source directory does not exist: " + dir, name, null, null)));
    }
    return new PathResult.Ok(dir);
  }

  private DraftRequest parse(ServerRequest request) throws IOException {
    try {
      return objectMapper.readValue(request.body(InputStream.class), DraftRequest.class);
    } catch (JsonProcessingException e) {
      log.warn("[DSL drafts] failed to parse request body: {}", e.getMessage());
      return null;
    } catch (jakarta.servlet.ServletException e) {
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

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) {
    return ServerResponse.status(status).body(body);
  }
}
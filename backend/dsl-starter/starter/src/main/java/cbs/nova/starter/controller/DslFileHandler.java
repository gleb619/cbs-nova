package cbs.nova.starter.controller;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslFileModels.BulkWriteRequest;
import cbs.nova.starter.model.DslFileModels.BulkWriteResult;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import cbs.nova.starter.model.DslFileModels.FlushResult;
import cbs.nova.starter.model.DslFileModels.PendingWritesStatus;
import cbs.nova.starter.model.FileEntityKind;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.service.DslFileService;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "cbs.dsl.files", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DslFileHandler {

  private final DslProperties dslProperties;
  private final DslFileService fileService;
  private final ObjectMapper objectMapper;

  public ServerResponse list(ServerRequest request) {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    String prefix = request.param("prefix").orElse(null);
    List<FileEntry> files = fileService.listFiles(prefix, kind);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(files);
  }

  public ServerResponse read(ServerRequest request) throws IOException {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    String path = pathVariable(request);
    if (path == null || path.isBlank()) {
      return badRequest("path is required");
    }
    return doReadFile(path, kind);
  }

  public ServerResponse write(ServerRequest request) throws IOException {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    String path = pathVariable(request);
    if (path == null || path.isBlank()) {
      return badRequest("path is required");
    }
    String content = readBody(request);
    fileService.stageWrite(path, content, kind);
    log.info("[{} files] staged write for {}", kind.name().toLowerCase(), path);
    return accepted(
            new FileContentResponse(path, content, FileContentResponse.crc32(content)));
  }

  public ServerResponse readByName(ServerRequest request) throws IOException {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    String name = nameVariable(request);
    if (name == null || name.isBlank()) {
      return badRequest("name is required");
    }
    try {
      FileContentResponse response = fileService.readFileByName(name, kind);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    } catch (BuilderApiException e) {
      if (e.getStatusCode().value() == 404) {
        return ServerResponse.notFound().build();
      }
      throw e;
    } catch (IllegalStateException e) {
      return serviceUnavailable(e.getMessage());
    }
  }

  public ServerResponse writeByName(ServerRequest request) throws IOException {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    String name = nameVariable(request);
    if (name == null || name.isBlank()) {
      return badRequest("name is required");
    }
    String content = readBody(request);
    try {
      FileContentResponse result = fileService.stageWriteByName(name, content, kind);
      log.info("[{} files] staged write for {} (resolved from {})", kind.name().toLowerCase(),
              result.path(), name);
      return accepted(result);
    } catch (BuilderApiException e) {
      if (e.getStatusCode().value() == 404) {
        return ServerResponse.notFound().build();
      }
      throw e;
    }
  }

  private ServerResponse doReadFile(String path, FileEntityKind kind) throws IOException {
    try {
      FileContentResponse response = fileService.readFile(path, kind);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    } catch (IllegalStateException e) {
      return serviceUnavailable(e.getMessage());
    }
  }

  public ServerResponse bulkWrite(ServerRequest request) throws IOException {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    BulkWriteRequest body;
    try {
      body = objectMapper.readValue(request.body(InputStream.class), BulkWriteRequest.class);
    } catch (Exception e) {
      log.warn("[{} files] failed to parse bulk write body: {}", kind.name().toLowerCase(),
              e.getMessage());
      return badRequest("malformed bulk write body");
    }
    if (body == null || body.files() == null || body.files().isEmpty()) {
      return badRequest("files list is required");
    }
    int staged = fileService.stageAll(body.files(), kind);
    List<String> errors = new ArrayList<>();
    if (staged < body.files().size()) {
      errors.add("some entries skipped because path was blank");
    }
    log.info("[{} files] bulk staged {}/{} files", kind.name().toLowerCase(), staged,
            body.files().size());
    return accepted(new BulkWriteResult(staged, body.files().size() - staged, errors));
  }

  public ServerResponse flush(ServerRequest request) {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    FlushResult result = fileService.flushPending(kind);
    log.info("[{} files] flush requested: {} flushed, {} failed", kind.name().toLowerCase(),
            result.flushed(), result.failed());
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(result);
  }

  public ServerResponse status(ServerRequest request) {
    var dirCheck = ensureConfigured();
    if (dirCheck.isError()) {
      return dirCheck.response();
    }
    FileEntityKind kind = kindParam(request);
    int pending = fileService.pendingCount(kind);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
            .body(new PendingWritesStatus(pending));
  }

  private String pathVariable(ServerRequest request) {
    String path = request.pathVariable("path")
            .replace('\\', '/').replaceAll("^/+", "");
    if (path.contains("..")) {
      return null;
    }
    return path;
  }

  private String nameVariable(ServerRequest request) {
    String name = request.pathVariable("name")
            .replace('\\', '/').replaceAll("^/+", "");
    if (name.contains("..")) {
      return null;
    }
    return name;
  }

  private FileEntityKind kindParam(ServerRequest request) {
    String kind = request.param("kind").orElse("DSL").trim().toUpperCase();
    try {
      return FileEntityKind.valueOf(kind);
    } catch (IllegalArgumentException e) {
      return FileEntityKind.DSL;
    }
  }

  private String readBody(ServerRequest request) throws IOException {
    try {
      String raw = request.body(String.class);
      if (raw.isBlank()) {
        return "";
      }
      try {
        FileContentRequest parsed = objectMapper.readValue(raw, FileContentRequest.class);
        if (parsed.content() != null) {
          return parsed.content();
        }
      } catch (Exception e) {
        return raw;
      }
      return raw;
    } catch (ServletException e) {
      throw new IOException("failed to read body", e);
    }
  }

  private ServerResponse accepted(Object body) {
    return ServerResponse.accepted().contentType(MediaType.APPLICATION_JSON).body(body);
  }

  private ServerResponse badRequest(String message) {
    return ServerResponse.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponse("INVALID_REQUEST", message, null, null, null, null, null,
                    null, null));
  }

  private ServerResponse serviceUnavailable(String message) {
    return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponse("BULKHEAD_SATURATED", message, null, null, null, null, null,
                    null, null));
  }

  private PathResult ensureConfigured() {
    String sourceDir = dslProperties.sourceDir();
    if (sourceDir == null || sourceDir.isBlank()) {
      return new PathResult.Err(error(HttpStatus.CONFLICT,
              new ErrorResponse("NOT_CONFIGURED", "cbs.dsl.source-dir is not configured", null,
                      null, null, null, null, null, null)));
    }
    return new PathResult.Ok(null);
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) {
    return ServerResponse.status(status).body(body);
  }

  private sealed interface PathResult {

    default boolean isError() {
      return false;
    }

    default ServerResponse response() {
      throw new IllegalStateException("not an error result");
    }

    record Ok(Object ignored) implements PathResult {
    }

    record Err(ServerResponse response) implements PathResult {

      @Override
      public boolean isError() {
        return true;
      }
    }
  }
}

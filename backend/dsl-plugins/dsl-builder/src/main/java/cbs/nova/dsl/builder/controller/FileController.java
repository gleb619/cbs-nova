package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.DslFileModels.BulkWriteRequest;
import cbs.nova.dsl.builder.model.DslFileModels.BulkWriteResult;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentRequest;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentResponse;
import cbs.nova.dsl.builder.model.DslFileModels.FileEntry;
import cbs.nova.dsl.builder.model.DslFileModels.FlushResult;
import cbs.nova.dsl.builder.model.DslFileModels.PendingWritesStatus;
import cbs.nova.dsl.builder.service.ByNamePathResolver;
import cbs.nova.dsl.builder.service.FileService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dsl/files")
public class FileController {

  private final FileService fileService;
  private final ByNamePathResolver pathResolver;
  private final ObjectMapper objectMapper;

  @GetMapping
  public List<FileEntry> list(@RequestParam(required = false) String prefix) {
    return fileService.listFiles(prefix);
  }

  @GetMapping("/{*path}")
  public FileContentResponse read(@PathVariable String path) throws IOException {
    String sanitized = sanitizePath(path);
    try {
      return fileService.readFile(sanitized);
    } catch (IOException e) {
      throw new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }
  }

  @GetMapping("/exists/{*path}")
  public boolean exists(@PathVariable String path) {
    return fileService.exists(sanitizePath(path));
  }

  @GetMapping("/by-name/{name}")
  public FileContentResponse readByName(@PathVariable String name) throws IOException {
    String resolved = resolveByName(name);
    try {
      return fileService.readFile(resolved);
    } catch (IOException e) {
      throw new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }
  }

  @PostMapping("/by-name/{name}")
  public ResponseEntity<FileContentResponse> writeByName(@PathVariable String name,
          @RequestBody(required = false) String rawBody) throws IOException {
    String resolved = resolveByName(name);
    String content = extractContent(rawBody);
    fileService.stageWrite(resolved, content);
    log.info("[DSL files] staged write for {} (resolved from {})", resolved, name);
    return ResponseEntity.accepted()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new FileContentResponse(resolved, content, true,
                    FileContentResponse.crc32(content)));
  }

  @PostMapping("/{*path}")
  public ResponseEntity<FileContentResponse> write(@PathVariable String path,
          @RequestBody(required = false) String rawBody) throws IOException {
    String sanitized = sanitizePath(path);
    String content = extractContent(rawBody);
    fileService.stageWrite(sanitized, content);
    log.info("[DSL files] staged write for {}", sanitized);
    return ResponseEntity.accepted()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new FileContentResponse(sanitized, content, true,
                    FileContentResponse.crc32(content)));
  }

  @PostMapping("/bulk")
  public ResponseEntity<BulkWriteResult> bulkWrite(@RequestBody BulkWriteRequest body) {
    if (body == null || body.files() == null || body.files().isEmpty()) {
      throw new IllegalArgumentException("files list is required");
    }
    int staged = fileService.stageAll(body.files());
    List<String> errors = new ArrayList<>();
    if (staged < body.files().size()) {
      errors.add("some entries skipped because path was blank");
    }
    log.info("[DSL files] bulk staged {}/{} files", staged, body.files().size());
    return ResponseEntity.accepted()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new BulkWriteResult(staged, body.files().size() - staged, errors));
  }

  @PostMapping("/flush")
  public FlushResult flush() {
    FlushResult result = fileService.flushPending();
    log.info("[DSL files] flush requested: {} flushed, {} failed", result.flushed(),
            result.failed());
    return result;
  }

  @GetMapping("/status")
  public PendingWritesStatus status() {
    return new PendingWritesStatus(fileService.pendingCount());
  }

  private String resolveByName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    return pathResolver.resolveSourcePath(name)
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no DSL file for name: " + name));
  }

  private String extractContent(String rawBody) throws IOException {
    if (rawBody == null || rawBody.isBlank()) {
      return "";
    }
    try {
      FileContentRequest parsed = objectMapper.readValue(rawBody, FileContentRequest.class);
      return parsed.content() != null ? parsed.content() : rawBody;
    } catch (Exception e) {
      return rawBody;
    }
  }

  private String sanitizePath(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path is required");
    }
    String sanitized = path.replace('\\', '/').replaceAll("^/+", "");
    if (sanitized.contains("..")) {
      throw new IllegalArgumentException("path escapes workspace: " + path);
    }
    return sanitized;
  }
}

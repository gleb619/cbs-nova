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
import cbs.nova.dsl.builder.service.DocsService;
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
@RequestMapping("/api/dsl/docs")
public class DocsController {

  private static final String MARKDOWN_SUFFIX = ".md";

  private final DocsService docsService;
  private final ByNamePathResolver pathResolver;
  private final ObjectMapper objectMapper;

  @GetMapping
  public List<FileEntry> list(@RequestParam(required = false) String prefix) {
    return docsService.listDocs(prefix);
  }

  @GetMapping("/{*path}")
  public FileContentResponse read(@PathVariable String path) throws IOException {
    String sanitized = sanitizeDocPath(path);
    try {
      return docsService.readDoc(sanitized);
    } catch (IOException e) {
      throw new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }
  }

  @GetMapping("/exists/{*path}")
  public boolean exists(@PathVariable String path) {
    return docsService.docExists(sanitizeDocPath(path));
  }

  @GetMapping("/by-name/{name}")
  public FileContentResponse readByName(@PathVariable String name) throws IOException {
    String resolved = resolveByName(name);
    try {
      return docsService.readDoc(resolved);
    } catch (IOException e) {
      throw new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }
  }

  @PostMapping("/by-name/{name}")
  public ResponseEntity<FileContentResponse> writeByName(@PathVariable String name,
          @RequestBody(required = false) String rawBody) throws IOException {
    requireName(name);
    // A missing doc means creation, not an error: fall back to the default kebab-case path.
    String resolved = pathResolver.resolveDocPath(name)
            .orElseGet(() -> pathResolver.defaultDocPath(name));
    String content = extractContent(rawBody);
    docsService.stageDocWrite(resolved, content);
    log.info("[DSL docs] staged write for {} (resolved from {})", resolved, name);
    return ResponseEntity.accepted()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new FileContentResponse(resolved, content, true,
                    FileContentResponse.crc32(content)));
  }

  @PostMapping("/{*path}")
  public ResponseEntity<FileContentResponse> write(@PathVariable String path,
          @RequestBody(required = false) String rawBody) throws IOException {
    String sanitized = sanitizeDocPath(path);
    String content = extractContent(rawBody);
    docsService.stageDocWrite(sanitized, content);
    log.info("[DSL docs] staged write for {}", sanitized);
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
    int staged = docsService.stageDocAll(body.files());
    List<String> errors = new ArrayList<>();
    if (staged < body.files().size()) {
      errors.add("some entries skipped because path was blank");
    }
    log.info("[DSL docs] bulk staged {}/{} files", staged, body.files().size());
    return ResponseEntity.accepted()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new BulkWriteResult(staged, body.files().size() - staged, errors));
  }

  @PostMapping("/flush")
  public FlushResult flush() {
    FlushResult result = docsService.flushDocs();
    log.info("[DSL docs] flush requested: {} flushed, {} failed", result.flushed(),
            result.failed());
    return result;
  }

  @GetMapping("/status")
  public PendingWritesStatus status() {
    return new PendingWritesStatus(docsService.pendingDocs());
  }

  private String resolveByName(String name) {
    requireName(name);
    return pathResolver.resolveDocPath(name)
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no DSL doc for name: " + name));
  }

  private void requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
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

  private String sanitizeDocPath(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path is required");
    }
    String sanitized = path.replace('\\', '/').replaceAll("^/+", "");
    if (sanitized.contains("..")) {
      throw new IllegalArgumentException("path escapes workspace: " + path);
    }
    if (!sanitized.endsWith(MARKDOWN_SUFFIX)) {
      throw new IllegalArgumentException("doc path must end with .md: " + path);
    }
    return sanitized;
  }
}

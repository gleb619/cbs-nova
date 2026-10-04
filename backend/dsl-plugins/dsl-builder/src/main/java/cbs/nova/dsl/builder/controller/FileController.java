package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.builder.exception.BuilderApiException;
import cbs.nova.dsl.builder.model.DslFileModels.BulkWriteRequest;
import cbs.nova.dsl.builder.model.DslFileModels.BulkWriteResult;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentRequest;
import cbs.nova.dsl.builder.model.DslFileModels.FileContentResponse;
import cbs.nova.dsl.builder.model.DslFileModels.FileEntry;
import cbs.nova.dsl.builder.model.DslFileModels.FlushResult;
import cbs.nova.dsl.builder.model.DslFileModels.PendingWritesStatus;
import cbs.nova.dsl.builder.model.FileEntityKind;
import cbs.nova.dsl.builder.service.ByNamePathResolver;
import cbs.nova.dsl.builder.service.FileEntityProfiles;
import cbs.nova.dsl.builder.service.FileService;
import java.io.IOException;
import java.util.Optional;
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
  public List<FileEntry> list(@RequestParam(required = false) String kind,
          @RequestParam(required = false) String prefix) {
    if (kind == null || kind.isBlank()) {
      return fileService.listFiles(prefix);
    }
    return fileService.listFiles(parseKind(kind), prefix);
  }

  @GetMapping("/{*path}")
  public FileContentResponse read(@RequestParam(required = false) String kind,
          @PathVariable String path) throws IOException {
    FileEntityKind entityKind = parseKind(kind);
    String sanitized = sanitizePath(entityKind, path);
    try {
      return fileService.readFile(toWorkspaceRelative(entityKind, sanitized));
    } catch (IOException e) {
      throw new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }
  }

  @GetMapping("/exists/{*path}")
  public boolean exists(@RequestParam(required = false) String kind,
          @PathVariable String path) {
    FileEntityKind entityKind = parseKind(kind);
    return fileService.exists(toWorkspaceRelative(entityKind, sanitizePath(entityKind, path)));
  }

  @GetMapping("/by-name/{name}")
  public FileContentResponse readByName(@RequestParam(required = false) String kind,
          @PathVariable String name) throws IOException {
    String resolved = resolveByName(kind, name);
    try {
      return fileService.readFile(resolved);
    } catch (IOException e) {
      throw new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }
  }

  @PostMapping("/by-name/{name}")
  public ResponseEntity<FileContentResponse> writeByName(
          @RequestParam(required = false) String kind,
          @PathVariable String name,
          @RequestBody(required = false) String rawBody) throws IOException {
    requireName(name);
    FileEntityKind entityKind = parseKind(kind);
    String resolved = pathResolver.resolvePath(name, entityKind)
            .or(() -> entityKind == FileEntityKind.DOC || entityKind == FileEntityKind.MODEL
                    ? Optional.of(pathResolver.defaultPath(name, entityKind))
                    : Optional.empty())
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no " + entityKind.name() + " file for name: " + name));
    String content = extractContent(rawBody);
    fileService.stageWrite(resolved, content);
    log.info("[DSL files] staged write for {} (resolved from {} kind={})", resolved, name,
            entityKind);
    return ResponseEntity.accepted()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new FileContentResponse(resolved, content, true,
                    FileContentResponse.crc32(content)));
  }

  @PostMapping("/{*path}")
  public ResponseEntity<FileContentResponse> write(
          @RequestParam(required = false) String kind,
          @PathVariable String path,
          @RequestBody(required = false) String rawBody) throws IOException {
    FileEntityKind entityKind = parseKind(kind);
    String sanitized = sanitizePath(entityKind, path);
    String content = extractContent(rawBody);
    fileService.stageWrite(toWorkspaceRelative(entityKind, sanitized), content);
    log.info("[DSL files] staged write for {} (kind={})", sanitized, entityKind);
    return ResponseEntity.accepted()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new FileContentResponse(sanitized, content, true,
                    FileContentResponse.crc32(content)));
  }

  @PostMapping("/bulk")
  public ResponseEntity<BulkWriteResult> bulkWrite(
          @RequestParam(required = false) String kind,
          @RequestBody BulkWriteRequest body) {
    if (body == null || body.files() == null || body.files().isEmpty()) {
      throw new IllegalArgumentException("files list is required");
    }
    FileEntityKind entityKind = parseKind(kind);
    List<FileContentRequest> workspaceRelative = body.files().stream()
            .map(f -> new FileContentRequest(
                    toWorkspaceRelative(entityKind, sanitizePath(entityKind, f.path())),
                    f.content() == null ? "" : f.content()))
            .toList();
    int staged = fileService.stageAll(workspaceRelative);
    List<String> errors = new ArrayList<>();
    if (staged < body.files().size()) {
      errors.add("some entries skipped because path was blank");
    }
    log.info("[DSL files] bulk staged {}/{} files (kind={})", staged, body.files().size(),
            entityKind);
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

  private String resolveByName(String kind, String name) {
    requireName(name);
    FileEntityKind entityKind = parseKind(kind);
    return pathResolver.resolvePath(name, entityKind)
            .orElseThrow(() -> new BuilderApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "no " + entityKind.name() + " file for name: " + name));
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

  private String toWorkspaceRelative(String kind, String relativePath) {
    return toWorkspaceRelative(parseKind(kind), relativePath);
  }

  private String toWorkspaceRelative(FileEntityKind kind, String relativePath) {
    if (kind != FileEntityKind.DOC) {
      return relativePath;
    }
    String docsSubDir = FileEntityProfiles.of(kind, null).rootSubDir();
    if (relativePath.startsWith(docsSubDir + "/")) {
      return relativePath;
    }
    return docsSubDir + "/" + relativePath;
  }

  private String sanitizePath(String kind, String path) {
    return sanitizePath(parseKind(kind), path);
  }

  private String sanitizePath(FileEntityKind kind, String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path is required");
    }
    String sanitized = path.replace('\\', '/').replaceAll("^/+", "");
    if (sanitized.contains("..")) {
      throw new IllegalArgumentException("path escapes workspace: " + path);
    }
    String suffix = FileEntityProfiles.of(kind, null).suffix();
    if (!sanitized.endsWith(suffix)) {
      throw new IllegalArgumentException(kind.name().toLowerCase() + " path must end with "
              + suffix + ": " + path);
    }
    return sanitized;
  }

  private static FileEntityKind parseKind(String kind) {
    if (kind == null || kind.isBlank()) {
      return FileEntityKind.DSL;
    }
    try {
      return FileEntityKind.valueOf(kind.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid kind: " + kind);
    }
  }
}

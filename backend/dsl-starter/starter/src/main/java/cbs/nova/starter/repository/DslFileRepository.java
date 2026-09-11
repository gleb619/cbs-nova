package cbs.nova.starter.repository;

import static cbs.nova.starter.core.StarterConstants.JAVA_FILE_SUFFIX;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.DslFileModels.FileEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Repository
public class DslFileRepository {

  public String read(Path root, String relativePath) throws IOException {
    Path file = resolve(root, relativePath);
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      StringBuilder builder = new StringBuilder();
      char[] buffer = new char[8192];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        builder.append(buffer, 0, read);
      }
      return builder.toString();
    }
  }

  /**
   * Writes {@code content} atomically: the full content is staged in a temp file in the same
   * directory and then published via {@link Files#move(Path, Path, CopyOption...)}
   * {@code ATOMIC_MOVE}. Concurrent readers therefore always observe a complete old or new version
   * of the file — never a truncated or interleaved one — which is what makes flush safe across
   * multiple app replicas sharing one workspace directory (see DslFileService).
   */
  public Path write(Path root, String relativePath, String content) throws IOException {
    Path file = resolve(root, relativePath);
    Files.createDirectories(file.getParent());
    Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
    try {
      Files.writeString(tmp, content, StandardCharsets.UTF_8);
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
    return file;
  }

  public List<FileEntry> list(Path root, String prefix) {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    List<FileEntry> entries = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root)) {
      stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(JAVA_FILE_SUFFIX))
              .filter(p -> prefix == null || prefix.isBlank()
                      || toRelative(root, p).startsWith(prefix))
              .forEach(p -> entries.add(toEntry(root, p)));
    } catch (IOException e) {
      log.warn("[DSL files] failed to list files under {}: {}", root, e.getMessage());
    }
    entries.sort(Comparator.comparing(FileEntry::path));
    return entries;
  }

  public boolean exists(Path root, String relativePath) {
    try {
      return Files.isRegularFile(resolve(root, relativePath));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private FileEntry toEntry(Path root, Path file) {
    String relative = toRelative(root, file);
    try {
      long size = Files.size(file);
      long modified = Files.getLastModifiedTime(file).toMillis();
      return new FileEntry(relative, size, modified);
    } catch (IOException e) {
      return new FileEntry(relative, 0L, 0L);
    }
  }

  private String toRelative(Path root, Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }

  private Path resolve(Path root, String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      throw new IllegalArgumentException("relative path is required");
    }
    String normalized = relativePath.replace('\\', '/')
            .replaceAll("/+", "/")
            .replaceAll("^/+", "");
    if (normalized.contains("..")) {
      throw new IllegalArgumentException("relative path escapes workspace: " + relativePath);
    }
    Path target = root.resolve(normalized).normalize();
    if (!target.startsWith(root.normalize())) {
      throw new IllegalArgumentException("resolved path escapes workspace: " + relativePath);
    }
    return target;
  }
}

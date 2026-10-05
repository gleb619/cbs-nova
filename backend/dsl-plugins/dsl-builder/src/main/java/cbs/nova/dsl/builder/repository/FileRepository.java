package cbs.nova.dsl.builder.repository;

import cbs.nova.dsl.builder.model.DslFileModels.FileEntry;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * File access for a DSL workspace (a git checkout).
 *
 * <p>
 * Containment policy: every resolved path is checked twice. First lexically (no {@code ..}, and the
 * normalized path must stay under the workspace root), then physically by comparing real paths
 * ({@link Path#toRealPath()}). The physical check closes the symlink escape hole that the lexical
 * check cannot see: a workspace containing {@code dsl -> /etc} or {@code a.java -> /etc/passwd}
 * passes every lexical check while still reading or writing outside the workspace.
 *
 * <p>
 * Documented decision: symlinks whose real target stays <em>inside</em> the workspace are allowed
 * (they cannot reach anything outside). Only links resolving outside the root are rejected, both as
 * the write target and as any existing ancestor of it.
 */

@Slf4j
@Repository
public class FileRepository {

  private static final String JAVA_SUFFIX = ".java";

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
    return list(root, prefix, JAVA_SUFFIX);
  }

  public List<FileEntry> list(Path root, String prefix, String suffix) {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    List<FileEntry> entries = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root)) {
      stream
              // NOFOLLOW_LINKS: a symlink is not a regular file entry itself, and following it here
              // would pull outside files into the walk.
              .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
              .filter(p -> p.getFileName().toString().endsWith(suffix))
              .filter(p -> prefix == null || prefix.isBlank()
                      || toRelative(root, p).startsWith(prefix))
              // Belt and braces: only report entries whose real path is still inside the root.
              .filter(p -> isInsideRoot(root, p))
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

  private boolean isInsideRoot(Path root, Path candidate) {
    try {
      return candidate.toRealPath().startsWith(root.toRealPath());
    } catch (IOException | RuntimeException e) {
      // Unresolvable entry (broken link, vanished file): skip it rather than guess.
      log.warn("[DSL files] skipping entry with unresolvable real path {}: {}", candidate,
              e.getMessage());
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
    ensureInsideRoot(root, target, relativePath);
    return target;
  }

  /**
   * Verifies with real paths that {@code target} still lives inside {@code root} after every
   * symbolic link on the way has been followed. The target itself may not exist yet (writes create
   * it), so containment is checked on the deepest existing ancestor and the remaining (necessarily
   * link-free) segments are appended lexically.
   *
   * @throws IllegalArgumentException
   *           if the target, or any existing ancestor of it, resolves outside the workspace root
   */
  private void ensureInsideRoot(Path root, Path target, String relativePath) {
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path realRoot;
    try {
      realRoot = normalizedRoot.toRealPath();
    } catch (IOException e) {
      // Workspace root itself is unusable (missing or unreadable): fail closed rather than
      // falling back to a purely lexical check.
      throw new IllegalArgumentException(
              "cannot verify real path of workspace root: " + normalizedRoot, e);
    }
    Path existing = existingAncestor(target.toAbsolutePath().normalize());
    Path realTarget;
    try {
      realTarget = existing.toRealPath()
              .resolve(existing.relativize(target.toAbsolutePath().normalize()))
              .normalize();
    } catch (IOException e) {
      // Existing ancestor exists but cannot be resolved (race, permissions): fail closed.
      throw new IllegalArgumentException(
              "cannot verify real path of resolved target: " + target, e);
    }
    if (!realTarget.startsWith(realRoot)) {
      throw new IllegalArgumentException(
              "resolved path escapes workspace via symlink: " + relativePath);
    }
  }

  /** Walks up from {@code target} to the first path that exists (links resolved). */
  private Path existingAncestor(Path target) {
    Path current = target;
    while (current != null && !Files.exists(current)) {
      current = current.getParent();
    }
    return current != null ? current : target;
  }
}

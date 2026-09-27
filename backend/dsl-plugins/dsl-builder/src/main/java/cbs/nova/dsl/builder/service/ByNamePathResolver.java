package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.utils.Strings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Maps a DSL construct {@code name} (the Workbench key, e.g. {@code "BatchProcessing"}) to a path
 * inside this builder's own work tree, so by-name endpoints never depend on the caller's
 * filesystem view of the workspace.
 *
 * <p>
 * Source files follow the {@code <name>Dsl.java} then {@code <name>.java} naming convention,
 * checked directly at the work-tree root first and then by recursive filename search. Docs follow
 * the explain-resource convention: kebab-case {@code <kebab>.md} first, then the raw
 * {@code <name>.md}, resolved under the docs sub-directory.
 *
 * <p>
 * Existence checks go through {@link FileService} so buffer-staged (not yet flushed) content is
 * respected; the recursive fallbacks scan the work tree on disk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ByNamePathResolver {

  private static final String DSL_FILE_SUFFIX = "Dsl.java";
  private static final String JAVA_SUFFIX = ".java";
  private static final String MARKDOWN_SUFFIX = ".md";

  private final WorkspaceService workspaceService;
  private final DslBuilderProperties properties;
  private final FileService fileService;

  /**
   * @param name
   *          construct name.
   * @return work-tree-relative forward-slash path of the DSL source file, or empty when no file
   *         matches the naming convention anywhere under the work tree.
   */
  public Optional<String> resolveSourcePath(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    Path workTree = workspaceService.workTree();
    List<String> candidates = List.of(name + DSL_FILE_SUFFIX, name + JAVA_SUFFIX);
    for (String candidate : candidates) {
      if (fileService.exists(candidate)) {
        return Optional.of(candidate);
      }
    }
    for (String candidate : candidates) {
      Optional<Path> found = findByFilename(workTree, candidate);
      if (found.isPresent()) {
        return Optional.of(relativize(workTree, found.get()));
      }
    }
    return Optional.empty();
  }

  /**
   * @param name
   *          construct name.
   * @return docs-root-relative forward-slash path of the existing doc, or empty when no doc
   *         matches under the docs root.
   */
  public Optional<String> resolveDocPath(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    String docsSubDir = properties.docs().subDir();
    List<String> candidates = List.of(defaultDocPath(name), name + MARKDOWN_SUFFIX);
    for (String candidate : candidates) {
      if (fileService.exists(docsSubDir + "/" + candidate)) {
        return Optional.of(candidate);
      }
    }
    Path docsRoot = workspaceService.workTree().resolve(docsSubDir);
    for (String candidate : candidates) {
      Optional<Path> found = findByFilename(docsRoot, candidate);
      if (found.isPresent()) {
        return Optional.of(relativize(docsRoot, found.get()));
      }
    }
    return Optional.empty();
  }

  /**
   * Docs-root-relative path a brand-new doc for {@code name} is written to: the kebab-case
   * convention path.
   */
  public String defaultDocPath(String name) {
    return Strings.toKebabCase(name) + MARKDOWN_SUFFIX;
  }

  private static Optional<Path> findByFilename(Path root, String filename) {
    if (root == null || !Files.isDirectory(root)) {
      return Optional.empty();
    }
    try (Stream<Path> stream = Files.find(root, Integer.MAX_VALUE,
            (path, _) -> Files.isRegularFile(path)
                    && path.getFileName().toString().equals(filename))) {
      return stream.findFirst();
    } catch (IOException e) {
      log.warn("[DSL by-name] failed to search {} for {}: {}", root, filename, e.getMessage());
      return Optional.empty();
    }
  }

  private static String relativize(Path root, Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }
}

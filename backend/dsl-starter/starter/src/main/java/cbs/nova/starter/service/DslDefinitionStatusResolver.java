package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.service.DslGitStatusResolver.RepoStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DslDefinitionStatusResolver {

  private static final String DRAFTS_DIR = ".workbench/drafts";
  private static final String PUBLISHED_DIR = ".workbench/published";
  private static final String JSON_SUFFIX = ".json";

  private final DslProperties dslProperties;
  private final DslGitStatusResolver gitResolver;

  public DefinitionStatus resolve(String name) {
    Map<String, DefinitionStatus> result = resolveAll(Set.of(name));
    return result.getOrDefault(name, DefinitionStatus.PUBLISHED);
  }

  public Map<String, DefinitionStatus> resolveAll(Collection<String> names) {
    Map<String, DefinitionStatus> result = new HashMap<>();
    if (names == null || names.isEmpty()) {
      return result;
    }

    Path sourceDir = sourceDir();
    if (sourceDir == null) {
      names.forEach(n -> result.put(n, DefinitionStatus.PUBLISHED));
      return result;
    }

    Optional<RepoStatus> git = gitResolver.status(sourceDir);
    Set<String> dirtyPaths = git.map(RepoStatus::dirtyPaths).orElse(Set.of());
    Path workTree = git.map(RepoStatus::workTree).orElse(sourceDir);

    for (String name : names) {
      if (Files.exists(safePath(sourceDir.resolve(DRAFTS_DIR), name))) {
        result.put(name, DefinitionStatus.DRAFT);
        continue;
      }

      Path publishedFile = safePath(sourceDir.resolve(PUBLISHED_DIR), name);
      if (git.isPresent() && isDirty(dirtyPaths, workTree, publishedFile)) {
        result.put(name, DefinitionStatus.MODIFIED);
        continue;
      }

      result.put(name, DefinitionStatus.PUBLISHED);
    }
    return result;
  }

  private Path sourceDir() {
    String sourceDirProperty = dslProperties.getSourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return null;
    }
    Path dir = Path.of(sourceDirProperty);
    return Files.isDirectory(dir) ? dir : null;
  }

  private boolean isDirty(Set<String> dirtyPaths, Path workTree, Path file) {
    if (!Files.exists(file)) {
      return false;
    }
    Path normalized = file.toAbsolutePath().normalize();
    String relative;
    if (normalized.startsWith(workTree)) {
      relative = workTree.relativize(normalized).toString();
    } else {
      relative = normalized.toString();
    }
    return dirtyPaths.contains(relative);
  }

  private static Path safePath(Path directory, String name) {
    Path file = directory.resolve(safeFileName(name) + JSON_SUFFIX).normalize();
    if (!file.startsWith(directory.normalize())) {
      throw new IllegalArgumentException("Illegal definition name: " + name);
    }
    return file;
  }

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}

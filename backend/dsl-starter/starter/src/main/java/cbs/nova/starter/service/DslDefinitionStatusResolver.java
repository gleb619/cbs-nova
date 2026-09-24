package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.service.DslGitStatusResolver.ChangeType;
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

/**
 * Resolves the {@link DefinitionStatus} of one or more DSL definitions.
 *
 * <p>
 * Per drafts spec §3.2 / §7 step 2: status comes from the <em>DSL source file</em> that declares
 * each definition, not from the {@code .workbench/...} JSON markers. The markers are retained as a
 * fallback only when git is absent or disabled.
 *
 * <p>
 * Resolution for each name (in this order):
 * <ol>
 * <li>{@link DslSourcePathResolver#relativePath(String)} maps the name to its DSL file path.</li>
 * <li>If git is enabled and a change is recorded for that path, the change's {@link ChangeType}
 * maps to the {@link DefinitionStatus}: ADDED/UNTRACKED → ADDED, MODIFIED → MODIFIED, DELETED →
 * DELETED, CONFLICTING → CONFLICTING.</li>
 * <li>If git had nothing for that path, the legacy {@code .workbench/drafts/<name>.json} marker is
 * consulted: present → DRAFT; absent → PUBLISHED.</li>
 * </ol>
 *
 * <p>
 * The git path can be reported under a builder worktree root that differs from the starter
 * source-dir, so a change key {@code K} matches the resolved path {@code P} if {@code K.equals(P)},
 * {@code K.endsWith("/" + P)}, or {@code P.endsWith("/" + K)}.
 */
@Component
@RequiredArgsConstructor
public class DslDefinitionStatusResolver {

  private final DslProperties dslProperties;
  private final DslGitStatusResolver gitResolver;
  private final DslSourcePathResolver sourcePathResolver;

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
    Map<String, ChangeType> changes = git.map(RepoStatus::changes).orElse(Map.of());

    for (String name : names) {
      Optional<String> resolved = sourcePathResolver.relativePath(name);
      ChangeType matched = resolved.flatMap(p -> matchChange(changes, p)).orElse(null);
      if (matched != null) {
        result.put(name, mapChangeType(matched));
        continue;
      }

      Path draftMarker = safePath(sourceDir.resolve(StarterConstants.WORKBENCH_DRAFTS_DIR), name);
      if (Files.exists(draftMarker)) {
        result.put(name, DefinitionStatus.DRAFT);
        continue;
      }
      result.put(name, DefinitionStatus.PUBLISHED);
    }
    return result;
  }

  private static DefinitionStatus mapChangeType(ChangeType type) {
    return switch (type) {
      case ADDED, UNTRACKED -> DefinitionStatus.ADDED;
      case MODIFIED -> DefinitionStatus.MODIFIED;
      case DELETED -> DefinitionStatus.DELETED;
      case CONFLICTING -> DefinitionStatus.CONFLICTING;
    };
  }

  /**
   * Find a git change key {@code K} in {@code changes} that matches the resolved source path
   * {@code P}. Match rule: {@code K.equals(P)} or {@code K.endsWith("/" + P)} or
   * {@code P.endsWith("/" + K)}. The third clause lets {@code "dsl/LoanDsl.java"} match a builder
   * key like {@code "repo/dsl/LoanDsl.java"}.
   */
  private static Optional<ChangeType> matchChange(Map<String, ChangeType> changes, String path) {
    if (path == null || path.isBlank()) {
      return Optional.empty();
    }
    ChangeType direct = changes.get(path);
    if (direct != null) {
      return Optional.of(direct);
    }
    String suffix = "/" + path;
    for (Map.Entry<String, ChangeType> e : changes.entrySet()) {
      String k = e.getKey();
      if (k != null && k.endsWith(suffix)) {
        return Optional.of(e.getValue());
      }
    }
    String pathSuffix = "/" + path;
    for (Map.Entry<String, ChangeType> e : changes.entrySet()) {
      String k = e.getKey();
      if (k != null && pathSuffix.endsWith("/" + k)) {
        return Optional.of(e.getValue());
      }
    }
    return Optional.empty();
  }

  private Path sourceDir() {
    String sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return null;
    }
    Path dir = Path.of(sourceDirProperty);
    return Files.isDirectory(dir) ? dir : null;
  }

  private static Path safePath(Path directory, String name) {
    Path file = directory.resolve(safeFileName(name) + StarterConstants.JSON_SUFFIX).normalize();
    if (!file.startsWith(directory.normalize())) {
      throw new IllegalArgumentException("Illegal definition name: " + name);
    }
    return file;
  }

  private static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}

package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.RepoStatus;
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
 * Per drafts spec §3: status comes from the <em>DSL source file</em> that declares each definition,
 * derived from git status. A clean file is {@code PUBLISHED}; there is no separate {@code DRAFT}
 * state.
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
      ChangeType matched = resolved.flatMap(p -> DslGitStatusResolver.matchChange(changes, p))
              .orElse(null);
      result.put(name, matched != null ? mapChangeType(matched) : DefinitionStatus.PUBLISHED);
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

  private Path sourceDir() {
    String sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return null;
    }
    Path dir = Path.of(sourceDirProperty);
    return Files.isDirectory(dir) ? dir : null;
  }
}

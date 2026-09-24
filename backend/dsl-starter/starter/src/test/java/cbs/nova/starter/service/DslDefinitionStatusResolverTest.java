package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.dsl.vcs.ChangeType;
import cbs.nova.dsl.vcs.RepoStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

/**
 * Spec §3: a definition's status is read from the git status of the DSL source file that declares
 * it. A clean file is PUBLISHED; there is no separate DRAFT state and no marker fallback.
 */
class DslDefinitionStatusResolverTest {

  private Path sourceDir;
  private DslGitStatusResolver gitResolver;
  private DslSourcePathResolver sourcePathResolver;
  private DslDefinitionStatusResolver resolver;

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-status-test-");
    DslProperties props = DslProperties.builder().sourceDir(sourceDir.toString()).build();
    gitResolver = mock(DslGitStatusResolver.class);
    sourcePathResolver = mock(DslSourcePathResolver.class);
    resolver = new DslDefinitionStatusResolver(props, gitResolver, sourcePathResolver);
    when(gitResolver.status(any())).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() throws IOException {
    if (sourceDir != null && Files.exists(sourceDir)) {
      try (Stream<Path> s = Files.walk(sourceDir)) {
        s.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (IOException ignored) {
          }
        });
      }
    }
  }

  @Test
  void addedWhenSourceFileAdded() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.ADDED));

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.ADDED);
  }

  @Test
  void untrackedMapsToAdded() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.UNTRACKED));

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.ADDED);
  }

  @Test
  void modifiedWhenSourceFileModified() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.MODIFIED));

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.MODIFIED);
  }

  @Test
  void deletedWhenSourceFileDeleted() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.DELETED));

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.DELETED);
  }

  @Test
  void conflictingWhenSourceFileConflicting() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.CONFLICTING));

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.CONFLICTING);
  }

  @Test
  void suffixMatchingWhenBuilderKeyIsPrefixed() {
    when(sourcePathResolver.relativePath("LoanDsl"))
            .thenReturn(Optional.of("dsl/LoanDsl.java"));
    stubGit(Map.of("repo/dsl/LoanDsl.java", ChangeType.MODIFIED));

    assertThat(resolver.resolve("LoanDsl")).isEqualTo(DefinitionStatus.MODIFIED);
  }

  @Test
  void publishedWhenCleanGit() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of());

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.PUBLISHED);
  }

  @Test
  void publishedWhenUnknownDefinition() {
    when(sourcePathResolver.relativePath("Foo")).thenReturn(Optional.empty());
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.MODIFIED));

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.PUBLISHED);
  }

  @Test
  void sourceDirNullReturnsPublished() {
    DslProperties propsNoDir = DslProperties.builder().build();
    DslDefinitionStatusResolver noDirResolver = new DslDefinitionStatusResolver(propsNoDir,
            gitResolver, sourcePathResolver);
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));

    assertThat(noDirResolver.resolve("Foo")).isEqualTo(DefinitionStatus.PUBLISHED);
  }

  @Test
  void resolvesAllInOneCall() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    when(sourcePathResolver.relativePath("Bar"))
            .thenReturn(Optional.of("dsl/BarDsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.MODIFIED));

    Map<String, DefinitionStatus> result = resolver.resolveAll(List.of("Foo", "Bar"));

    assertThat(result).containsEntry("Foo", DefinitionStatus.MODIFIED)
            .containsEntry("Bar", DefinitionStatus.PUBLISHED);
  }

  @Test
  void resolvesAllWithoutDuplicatedSourcePathCalls() {
    when(sourcePathResolver.relativePath(any()))
            .thenAnswer((InvocationOnMock inv) -> Optional
                    .of("dsl/" + inv.getArgument(0) + "Dsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.ADDED));

    Map<String, DefinitionStatus> result = resolver.resolveAll(List.of("Foo", "Foo"));

    assertThat(result).containsEntry("Foo", DefinitionStatus.ADDED);
    org.mockito.Mockito.verify(sourcePathResolver, org.mockito.Mockito.times(2))
            .relativePath("Foo");
  }

  private void stubGit(Map<String, ChangeType> changes) {
    when(gitResolver.status(any())).thenReturn(
            Optional.of(new RepoStatus(sourceDir, changes.keySet(), changes)));
  }
}

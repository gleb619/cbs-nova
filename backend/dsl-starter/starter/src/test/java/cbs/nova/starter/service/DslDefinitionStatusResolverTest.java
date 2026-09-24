package cbs.nova.starter.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import cbs.nova.starter.service.DslGitStatusResolver.ChangeType;
import cbs.nova.starter.service.DslGitStatusResolver.RepoStatus;
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
 * Spec §7 step 2 / §3.2: a definition's status is read from the git status of the DSL <em>source
 * file</em> that declares it. The marker JSON files are a fallback only when git is absent.
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
    // Builder worktree root differs from starter source-dir; key carries the prefix.
    when(sourcePathResolver.relativePath("LoanDsl"))
            .thenReturn(Optional.of("dsl/LoanDsl.java"));
    stubGit(Map.of("repo/dsl/LoanDsl.java", ChangeType.MODIFIED));

    assertThat(resolver.resolve("LoanDsl")).isEqualTo(DefinitionStatus.MODIFIED);
  }

  @Test
  void draftMarkerFallbackWhenCleanGitAndNoChange() throws IOException {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of());

    Path draft = sourceDir.resolve(".workbench/drafts/Foo.json");
    Files.createDirectories(draft.getParent());
    Files.writeString(draft, "{}", UTF_8);

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.DRAFT);
  }

  @Test
  void publishedWhenCleanGitAndNoMarker() {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of());

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.PUBLISHED);
  }

  @Test
  void publishedWhenUnknownDefinition() {
    // Empty Optional from source path resolver (no provider, no fallback).
    when(sourcePathResolver.relativePath("Foo")).thenReturn(Optional.empty());
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.MODIFIED));

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.PUBLISHED);
  }

  @Test
  void gitAbsentFallsBackToMarker() throws IOException {
    when(gitResolver.status(any())).thenReturn(Optional.empty());
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));

    Path draft = sourceDir.resolve(".workbench/drafts/Foo.json");
    Files.createDirectories(draft.getParent());
    Files.writeString(draft, "{}", UTF_8);

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.DRAFT);
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
  void gitChangeWinsOverMarker() throws IOException {
    when(sourcePathResolver.relativePath("Foo"))
            .thenReturn(Optional.of("dsl/FooDsl.java"));
    stubGit(Map.of("dsl/FooDsl.java", ChangeType.DELETED));

    // Marker would otherwise say DRAFT; git DELETE must win.
    Path draft = sourceDir.resolve(".workbench/drafts/Foo.json");
    Files.createDirectories(draft.getParent());
    Files.writeString(draft, "{}", UTF_8);

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.DELETED);
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

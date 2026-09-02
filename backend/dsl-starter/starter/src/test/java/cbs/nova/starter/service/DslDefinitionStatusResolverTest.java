package cbs.nova.starter.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionStatus;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

class DslDefinitionStatusResolverTest {

  private Path sourceDir;
  private DslDefinitionStatusResolver resolver;

  @BeforeEach
  void setUp() throws IOException {
    sourceDir = Files.createTempDirectory("dsl-status-test-");
    DslProperties props = new DslProperties();
    props.setSourceDir(sourceDir.toString());
    resolver = new DslDefinitionStatusResolver(props, new DslGitStatusResolver(props));
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
  void publishedWhenNoMarkers() {
    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.PUBLISHED);
  }

  @Test
  void draftWhenDraftMarkerExists() throws IOException {
    Path draft = sourceDir.resolve(".workbench/drafts/Foo.json");
    Files.createDirectories(draft.getParent());
    Files.writeString(draft, "{}", UTF_8);

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.DRAFT);
  }

  @Test
  void publishedWhenOnlyPublishedMarkerExists() throws IOException {
    Path published = sourceDir.resolve(".workbench/published/Foo.json");
    Files.createDirectories(published.getParent());
    Files.writeString(published, "{}", UTF_8);

    assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.PUBLISHED);
  }

  @Test
  void modifiedWhenPublishedMarkerIsDirtyInGit() throws Exception {
    Git git = Git.init().setDirectory(sourceDir.toFile()).call();
    try {
      Path published = sourceDir.resolve(".workbench/published/Foo.json");
      Files.createDirectories(published.getParent());
      Files.writeString(published, "{}", UTF_8);
      git.add().addFilepattern(".").call();
      git.commit().setMessage("initial").call();

      Files.writeString(published, "{\"status\":\"Published\"}", UTF_8);

      assertThat(resolver.resolve("Foo")).isEqualTo(DefinitionStatus.MODIFIED);
    } finally {
      git.close();
    }
  }

  @Test
  void resolvesAllInOneCall() throws IOException {
    Path draft = sourceDir.resolve(".workbench/drafts/Bar.json");
    Files.createDirectories(draft.getParent());
    Files.writeString(draft, "{}", UTF_8);

    Map<String, DefinitionStatus> result = resolver.resolveAll(java.util.List.of("Foo", "Bar"));

    assertThat(result).containsEntry("Foo", DefinitionStatus.PUBLISHED)
            .containsEntry("Bar", DefinitionStatus.DRAFT);
  }
}

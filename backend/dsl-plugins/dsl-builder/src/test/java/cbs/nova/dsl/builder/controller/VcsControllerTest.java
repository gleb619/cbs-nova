package cbs.nova.dsl.builder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.builder.config.BuilderServiceConfiguration;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.repository.FileRepository;
import cbs.nova.dsl.builder.service.FileService;
import cbs.nova.dsl.builder.service.GitStatusService;
import cbs.nova.dsl.builder.service.WorkspaceGitService;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VcsController.class)
@Import({GitStatusService.class, FileService.class, FileRepository.class,
    WorkspaceGitService.class, BuilderServiceConfiguration.class, VcsControllerTest.Config.class})
class VcsControllerTest {

  private static final Path WORKSPACE = Path.of(System.getProperty("java.io.tmpdir"),
          "dsl-builder-vcs-mvc-" + System.nanoTime());

  @Autowired
  MockMvc mockMvc;

  @Autowired
  org.springframework.beans.factory.ObjectProvider<FileService> fileServiceRef;

  @BeforeEach
  void cleanWorkspace() throws IOException {
    if (!Files.exists(WORKSPACE)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(WORKSPACE)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.delete(path);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      });
    }
  }

  @TestConfiguration
  static class Config {

    @Bean
    DslBuilderProperties dslBuilderProperties() {
      return new DslBuilderProperties(
              WORKSPACE,
              Duration.ofMinutes(10),
              Duration.ofHours(1),
              null,
              "0.0.1-SNAPSHOT",
              "1.27.0",
              "4.0.4",
              "v1",
              List.of("clean", "build"),
              List.of("dsl", "models"),
              "project/templates",
              null,
              null,
              null,
              null,
              new DslBuilderProperties.Git(true, null, null, null, null,
                      null, 0),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null);
    }
  }

  @Test
  void returnsNotFoundWhenNoRepositoryExists() throws Exception {
    mockMvc.perform(get("/api/dsl/vcs/status"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void reportsDirtyPathsForWorkspaceRepository() throws Exception {
    initRepositoryWithUntrackedFile();

    mockMvc.perform(get("/api/dsl/vcs/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workTree").isString())
            .andExpect(jsonPath("$.dirtyPaths").isArray())
            .andExpect(jsonPath("$.dirtyPaths[0]").value("dsl/LoanDsl.java"))
            .andExpect(jsonPath("$.changes['dsl/LoanDsl.java']").value("UNTRACKED"));
  }

  @Test
  void flushesPendingWritesBeforeReportingStatus() throws Exception {
    initRepositoryWithUntrackedFile();
    FileService fileService = fileServiceRef.getIfAvailable();
    fileService.stageWrite("dsl/StagedDsl.java", "class StagedDsl {}");
    assertThat(fileService.pendingCount()).isPositive();

    // Gap G6 / invariant I5: the file is still in the pending buffer when status is requested.
    // The controller must flush it before scanning so it shows up as a draft on the same request.
    mockMvc.perform(get("/api/dsl/vcs/status"))
            .andExpect(status().isOk())
            .andExpect(
                    jsonPath("$.dirtyPaths", org.hamcrest.Matchers.hasItem("dsl/StagedDsl.java")))
            .andExpect(jsonPath("$.changes['dsl/StagedDsl.java']").value("UNTRACKED"));
    assertThat(fileService.pendingCount()).isZero();
  }

  private void initRepositoryWithUntrackedFile() throws IOException {
    Files.createDirectories(WORKSPACE.resolve("dsl"));
    Files.writeString(WORKSPACE.resolve("dsl/LoanDsl.java"), "class LoanDsl {}");
    try (Git git = Git.init().setDirectory(WORKSPACE.toFile()).call()) {
      assertThat(git.getRepository().getWorkTree().toPath()).isEqualTo(WORKSPACE.toFile().toPath());
    } catch (Exception e) {
      throw new IOException("failed to init repository", e);
    }
  }

  private void initRepositoryWithInitialCommit() throws IOException {
    Files.createDirectories(WORKSPACE.resolve("dsl"));
    Files.writeString(WORKSPACE.resolve("dsl/LoanDsl.java"), "class LoanDsl {}");
    Files.writeString(WORKSPACE.resolve("README.md"), "v1");
    try (Git git = Git.init().setDirectory(WORKSPACE.toFile()).call()) {
      git.add().addFilepattern(".").call();
      git.commit().setMessage("initial").setAuthor("a", "a@example.com")
              .setCommitter("a", "a@example.com").call();
    } catch (Exception e) {
      throw new IOException("failed to init repository", e);
    }
  }

  private String headCommitId() throws Exception {
    try (Git git = Git.open(WORKSPACE.toFile())) {
      return git.log().call().iterator().next().getName();
    }
  }

  @Test
  void commitEndpointPublishesDirtyPaths() throws Exception {
    initRepositoryWithUntrackedFile();

    String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
            "paths", java.util.List.of("dsl/LoanDsl.java"),
            "message", "publish loan",
            "authorName", "actor",
            "authorEmail", "actor@example.com"));

    mockMvc.perform(post("/api/dsl/vcs/commit")
            .contentType("application/json")
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commitId").isString())
            .andExpect(jsonPath("$.paths[0]").value("dsl/LoanDsl.java"))
            .andExpect(jsonPath("$.timestampMillis").isNumber());

    try (Git git = Git.open(WORKSPACE.toFile())) {
      assertThat(git.log().call().iterator().next().getShortMessage()).isEqualTo("publish loan");
    }
  }

  @Test
  void commitEndpointRejectsEmptyPathsWith400() throws Exception {
    initRepositoryWithUntrackedFile();
    String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
            "paths", java.util.List.of(),
            "message", "noop",
            "authorName", "actor",
            "authorEmail", "actor@example.com"));

    mockMvc.perform(post("/api/dsl/vcs/commit")
            .contentType("application/json")
            .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PATH"));
  }

  @Test
  void commitEndpointRejectsBlankMessageWith400() throws Exception {
    initRepositoryWithUntrackedFile();
    String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
            "paths", java.util.List.of("dsl/LoanDsl.java"),
            "message", "  ",
            "authorName", "actor",
            "authorEmail", "actor@example.com"));

    mockMvc.perform(post("/api/dsl/vcs/commit")
            .contentType("application/json")
            .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PATH"));
  }

  @Test
  void commitEndpointFlushesPendingBeforeCommit() throws Exception {
    initRepositoryWithUntrackedFile();
    FileService fileService = fileServiceRef.getIfAvailable();
    fileService.stageWrite("dsl/BufferedDsl.java", "class BufferedDsl {}");
    assertThat(fileService.pendingCount()).isPositive();

    String body = new ObjectMapper().writeValueAsString(java.util.Map.of(
            "paths", java.util.List.of("dsl/BufferedDsl.java"),
            "message", "publish buffered",
            "authorName", "actor",
            "authorEmail", "actor@example.com"));

    mockMvc.perform(post("/api/dsl/vcs/commit")
            .contentType("application/json")
            .content(body))
            .andExpect(status().isOk());

    assertThat(fileService.pendingCount()).isZero();
    assertThat(Files.exists(WORKSPACE.resolve("dsl/BufferedDsl.java"))).isTrue();
  }

  @Test
  void discardEndpointRemovesDirtyFile() throws Exception {
    initRepositoryWithUntrackedFile();
    assertThat(Files.exists(WORKSPACE.resolve("dsl/LoanDsl.java"))).isTrue();

    String body = new ObjectMapper().writeValueAsString(
            java.util.Map.of("paths", java.util.List.of("dsl/LoanDsl.java")));

    mockMvc.perform(post("/api/dsl/vcs/discard")
            .contentType("application/json")
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discarded[0]").value("dsl/LoanDsl.java"));

    assertThat(Files.exists(WORKSPACE.resolve("dsl/LoanDsl.java"))).isFalse();
  }

  @Test
  void discardEndpointRejectsEmptyPathsWith400() throws Exception {
    initRepositoryWithUntrackedFile();
    String body = new ObjectMapper().writeValueAsString(
            java.util.Map.of("paths", java.util.List.of()));

    mockMvc.perform(post("/api/dsl/vcs/discard")
            .contentType("application/json")
            .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PATH"));
  }

  @Test
  void logEndpointReturnsHistoryNewestFirst() throws Exception {
    initRepositoryWithUntrackedFile();
    Files.writeString(WORKSPACE.resolve("dsl/LoanDsl.java"), "class LoanDsl {}");
    try (Git git = Git.open(WORKSPACE.toFile())) {
      git.add().addFilepattern("dsl/LoanDsl.java").call();
      git.commit().setMessage("v2").setAuthor("a", "a@example.com")
              .setCommitter("a", "a@example.com").call();
    }

    mockMvc.perform(get("/api/dsl/vcs/log").param("path", "dsl/LoanDsl.java").param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].message").value("v2"))
            .andExpect(jsonPath("$[0].commitId").isString())
            .andExpect(jsonPath("$[0].timestampMillis").isNumber());
  }

  @Test
  void showEndpointReturnsContentForCommit() throws Exception {
    initRepositoryWithUntrackedFile();
    Files.writeString(WORKSPACE.resolve("dsl/LoanDsl.java"), "class LoanDslV2 {}");
    RevCommit head;
    try (Git git = Git.open(WORKSPACE.toFile())) {
      git.add().addFilepattern("dsl/LoanDsl.java").call();
      head = git.commit().setMessage("v2").setAuthor("a", "a@example.com")
              .setCommitter("a", "a@example.com").call();
    }

    mockMvc.perform(get("/api/dsl/vcs/show")
            .param("path", "dsl/LoanDsl.java")
            .param("commit", head.getName()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("dsl/LoanDsl.java"))
            .andExpect(jsonPath("$.commitId").value(head.getName()))
            .andExpect(jsonPath("$.content").value("class LoanDslV2 {}"));
  }

  @Test
  void showEndpointReturns404WhenPathAbsentAtCommit() throws Exception {
    initRepositoryWithInitialCommit();
    String head = headCommitId();

    mockMvc.perform(get("/api/dsl/vcs/show")
            .param("path", "dsl/Missing.java")
            .param("commit", head))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void showEndpointReturns404ForUnknownCommit() throws Exception {
    initRepositoryWithInitialCommit();

    mockMvc.perform(get("/api/dsl/vcs/show")
            .param("path", "dsl/LoanDsl.java")
            .param("commit", "0000000000000000000000000000000000000000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}

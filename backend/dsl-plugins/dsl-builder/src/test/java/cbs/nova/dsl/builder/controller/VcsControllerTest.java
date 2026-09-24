package cbs.nova.dsl.builder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.builder.config.BuilderServiceConfiguration;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.repository.FileRepository;
import cbs.nova.dsl.builder.service.FileService;
import cbs.nova.dsl.builder.service.GitStatusService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
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
    BuilderServiceConfiguration.class, VcsControllerTest.Config.class})
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
}

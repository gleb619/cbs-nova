package cbs.nova.dsl.builder.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.builder.config.BuilderServiceConfiguration;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.repository.FileRepository;
import cbs.nova.dsl.builder.service.ByNamePathResolver;
import cbs.nova.dsl.builder.service.DocsService;
import cbs.nova.dsl.builder.service.FileService;
import cbs.nova.dsl.builder.service.GitService;
import cbs.nova.dsl.builder.service.WorkspaceService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocsController.class)
@Import({DocsService.class, FileService.class, FileRepository.class, WorkspaceService.class,
    GitService.class,
    BuilderServiceConfiguration.class, ByNamePathResolver.class,
    DocsControllerTest.Config.class})
class DocsControllerTest {

  private static final Path WORKSPACE = Path.of(System.getProperty("java.io.tmpdir"),
          "dsl-builder-docs-mvc-" + System.nanoTime());

  @Autowired
  MockMvc mockMvc;

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
              new DslBuilderProperties.Files(0, 100, 32, 8, 5L),
              null,
              null,
              new DslBuilderProperties.Bundles(1, 200),
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              null);
    }
  }

  @Test
  void stagesFlushesAndReadsDocs() throws Exception {
    mockMvc.perform(post("/api/dsl/docs/loan-disbursement.md")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                    "{\"path\":\"loan-disbursement.md\",\"content\":\"# Loan\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.path").value("loan-disbursement.md"))
            .andExpect(jsonPath("$.pending").value(true));

    mockMvc.perform(get("/api/dsl/docs/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pending").value(1));

    mockMvc.perform(get("/api/dsl/docs/loan-disbursement.md"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("# Loan"))
            .andExpect(jsonPath("$.pending").value(true));

    mockMvc.perform(post("/api/dsl/docs/flush"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flushed").value(1))
            .andExpect(jsonPath("$.failed").value(0));

    mockMvc.perform(get("/api/dsl/docs/loan-disbursement.md"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("# Loan"))
            .andExpect(jsonPath("$.pending").value(false));

    mockMvc.perform(get("/api/dsl/docs/exists/loan-disbursement.md"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(true));
  }

  @Test
  void acceptsRawTextWriteBody() throws Exception {
    mockMvc.perform(post("/api/dsl/docs/raw-doc.md")
            .contentType(MediaType.TEXT_PLAIN)
            .content("raw markdown"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.content").value("raw markdown"));

    mockMvc.perform(post("/api/dsl/docs/flush"))
            .andExpect(status().isOk());
  }

  @Test
  void listsDocsWithPrefix() throws Exception {
    mockMvc.perform(post("/api/dsl/docs/bulk")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"files":[
                      {"path":"loan.md","content":"# Loan"},
                      {"path":"guides/approval.md","content":"# Approval"}
                    ]}
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.staged").value(2))
            .andExpect(jsonPath("$.failed").value(0));

    mockMvc.perform(post("/api/dsl/docs/flush"))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/dsl/docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));

    mockMvc.perform(get("/api/dsl/docs").param("prefix", "guides"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].path").value("guides/approval.md"));
  }

  @Test
  void reportsMissingDocs() throws Exception {
    mockMvc.perform(get("/api/dsl/docs/exists/missing.md"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(false));

    mockMvc.perform(get("/api/dsl/docs/missing.md"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void rejectsEscapingPaths() throws Exception {
    mockMvc.perform(get("/api/dsl/docs/../secret.md"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void rejectsNonMarkdownPaths() throws Exception {
    mockMvc.perform(get("/api/dsl/docs/notes.txt"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    mockMvc.perform(post("/api/dsl/docs/notes.txt")
            .contentType(MediaType.TEXT_PLAIN)
            .content("oops"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void createsAndReadsDocByName() throws Exception {
    mockMvc.perform(post("/api/dsl/docs/by-name/BatchProcessing")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"# Batch\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.path").value("batch-processing.md"));

    mockMvc.perform(get("/api/dsl/docs/by-name/BatchProcessing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("docs/batch-processing.md"))
            .andExpect(jsonPath("$.content").value("# Batch"));
  }

  @Test
  void readByNameReturns404ForUnknownName() throws Exception {
    mockMvc.perform(get("/api/dsl/docs/by-name/Missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("no DSL doc for name: Missing"));
  }

  @Test
  void readsDocByFrontmatterNameWhenFileNameDiffers() throws Exception {
    mockMvc.perform(post("/api/dsl/docs/approval-flow.md")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                    """
                            {"path":"approval-flow.md","content":"---\\nname: ApprovalFlow\\n---\\n\\n# ApprovalFlow"}
                            """))
            .andExpect(status().isAccepted());
    mockMvc.perform(post("/api/dsl/docs/flush"))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/dsl/docs/by-name/ApprovalFlow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("docs/approval-flow.md"))
            .andExpect(jsonPath("$.content").value(containsString("name: ApprovalFlow")));
  }

  @Test
  void writesExistingDocByName() throws Exception {
    mockMvc.perform(post("/api/dsl/docs/batch-processing.md")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"path\":\"batch-processing.md\",\"content\":\"# v1\"}"))
            .andExpect(status().isAccepted());
    mockMvc.perform(post("/api/dsl/docs/flush"))
            .andExpect(status().isOk());

    mockMvc.perform(post("/api/dsl/docs/by-name/BatchProcessing")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"# v2\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.path").value("batch-processing.md"));

    mockMvc.perform(get("/api/dsl/docs/batch-processing.md"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("# v2"));
  }
}

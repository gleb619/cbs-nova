package cbs.nova.dsl.builder.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.service.DefinitionBundleService;
import cbs.nova.dsl.builder.service.DefinitionHistoryService;
import cbs.nova.dsl.builder.service.DraftService;
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

@WebMvcTest({DraftController.class, DefinitionBundleController.class})
@Import({DraftService.class, DefinitionHistoryService.class, DefinitionBundleService.class,
    DraftControllerTest.Config.class})
class DraftControllerTest {

  private static final Path WORKSPACE = Path.of(System.getProperty("java.io.tmpdir"),
          "dsl-builder-draft-mvc-" + System.nanoTime());

  private static final String DRAFT_BODY = """
          {"name":"DiffProcess","type":"process","version":"v1","taskQueue":"q"}
          """;

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
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
    }
  }

  @Test
  void savesPublishesListsAndReadsDrafts() throws Exception {
    mockMvc.perform(post("/api/dsl/drafts/{name}/save", "DiffProcess")
            .contentType(MediaType.APPLICATION_JSON)
            .content(DRAFT_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("DiffProcess"))
            .andExpect(jsonPath("$.status").value("Draft"))
            .andExpect(jsonPath("$.reloaded").value(false))
            .andExpect(jsonPath("$.loadResult").exists());

    mockMvc.perform(get("/api/dsl/drafts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].name").value("DiffProcess"));

    mockMvc.perform(get("/api/dsl/drafts/{name}", "DiffProcess"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("DiffProcess"))
            .andExpect(jsonPath("$.status").value("Draft"));

    mockMvc.perform(post("/api/dsl/drafts/{name}/publish", "DiffProcess")
            .contentType(MediaType.APPLICATION_JSON)
            .content(DRAFT_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Published"))
            .andExpect(jsonPath("$.reloaded").value(false))
            .andExpect(jsonPath("$.reloadError").doesNotExist());

    mockMvc.perform(get("/api/dsl/drafts/{name}/history", "DiffProcess"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void publishSnapshotsHistoryAndSupportsDiffAndRestore() throws Exception {
    String v1 = """
            {"name":"DiffProcess","type":"process","version":"v1","taskQueue":"q"}
            """;
    String v2 = """
            {"name":"DiffProcess","type":"process","version":"v2","taskQueue":"q"}
            """;

    mockMvc.perform(post("/api/dsl/drafts/{name}/publish", "DiffProcess")
            .contentType(MediaType.APPLICATION_JSON).content(v1))
            .andExpect(status().isOk());
    mockMvc.perform(post("/api/dsl/drafts/{name}/publish", "DiffProcess")
            .contentType(MediaType.APPLICATION_JSON).content(v2))
            .andExpect(status().isOk());

    String history = mockMvc.perform(get("/api/dsl/drafts/{name}/history", "DiffProcess"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].timestamp").isString())
            .andReturn().getResponse().getContentAsString();
    String timestamp = history.replaceAll(".*\\\"timestamp\\\":\\\"([0-9]+)\\\".*", "$1");

    mockMvc.perform(get("/api/dsl/drafts/{name}/history/{ts}", "DiffProcess", timestamp))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("v1"));

    mockMvc.perform(get("/api/dsl/drafts/{name}/history/{ts}/diff", "DiffProcess", timestamp))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.before").isString())
            .andExpect(jsonPath("$.after").isString())
            .andExpect(jsonPath("$.hunks").isArray())
            .andExpect(jsonPath("$.truncated").value(false));

    mockMvc.perform(post("/api/dsl/drafts/{name}/history/{ts}/restore", "DiffProcess", timestamp))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Published"))
            .andExpect(jsonPath("$.reloaded").value(false));
  }

  @Test
  void missingDraftAndHistoryReturnNotFound() throws Exception {
    mockMvc.perform(get("/api/dsl/drafts/{name}", "Missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    mockMvc.perform(get("/api/dsl/drafts/{name}/history/{ts}", "Missing", "123"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void saveWithoutNameReturnsBadRequest() throws Exception {
    mockMvc.perform(post("/api/dsl/drafts/{name}/save", "DiffProcess")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"process\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void deleteRemovesDraft() throws Exception {
    mockMvc.perform(post("/api/dsl/drafts/{name}/save", "ToDelete")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"name":"ToDelete","type":"process","version":"v1","taskQueue":"q"}
                    """))
            .andExpect(status().isOk());

    mockMvc.perform(delete("/api/dsl/drafts/{name}", "ToDelete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Deleted"));

    mockMvc.perform(get("/api/dsl/drafts/{name}", "ToDelete"))
            .andExpect(status().isNotFound());
  }

  @Test
  void exportsAndImportsBundles() throws Exception {
    mockMvc.perform(post("/api/dsl/drafts/{name}/publish", "LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"name":"LoanA","type":"process","version":"v1","taskQueue":"q"}
                    """))
            .andExpect(status().isOk());

    String bundle = mockMvc.perform(get("/api/dsl/definitions/export"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.formatVersion").value(1))
            .andExpect(jsonPath("$.definitions[0].definition.name").value("LoanA"))
            .andReturn().getResponse().getContentAsString();

    mockMvc.perform(post("/api/dsl/definitions/import?dryRun=true")
            .contentType(MediaType.APPLICATION_JSON)
            .content(bundle))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.results[0].outcome").value("skipped"));

    mockMvc.perform(post("/api/dsl/definitions/import")
            .contentType(MediaType.APPLICATION_JSON)
            .content(bundle))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(false))
            .andExpect(jsonPath("$.reloaded").value(false))
            .andExpect(jsonPath("$.published").value(1))
            .andExpect(jsonPath("$.results[0].outcome").value("published"));
  }
}

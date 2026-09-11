package cbs.nova.dsl.builder.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.repository.FileRepository;
import cbs.nova.dsl.builder.service.FileBuffer;
import cbs.nova.dsl.builder.service.FileBulkhead;
import cbs.nova.dsl.builder.service.FileService;
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

@WebMvcTest(FileController.class)
@Import({FileService.class, FileRepository.class, FileBuffer.class, FileBulkhead.class,
    FileControllerTest.Config.class})
class FileControllerTest {

  private static final Path WORKSPACE = Path.of(System.getProperty("java.io.tmpdir"),
          "dsl-builder-file-mvc-" + System.nanoTime());

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
              null);
    }
  }

  @Test
  void stagesFlushesAndReadsFiles() throws Exception {
    mockMvc.perform(post("/api/dsl/files/dsl/LoanDsl.java")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"path\":\"dsl/LoanDsl.java\",\"content\":\"class LoanDsl {}\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.path").value("dsl/LoanDsl.java"))
            .andExpect(jsonPath("$.pending").value(true));

    mockMvc.perform(get("/api/dsl/files/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pending").value(1));

    mockMvc.perform(get("/api/dsl/files/dsl/LoanDsl.java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("class LoanDsl {}"))
            .andExpect(jsonPath("$.pending").value(true));

    mockMvc.perform(post("/api/dsl/files/flush"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flushed").value(1))
            .andExpect(jsonPath("$.failed").value(0));

    mockMvc.perform(get("/api/dsl/files/dsl/LoanDsl.java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("class LoanDsl {}"))
            .andExpect(jsonPath("$.pending").value(false));
  }

  @Test
  void acceptsRawTextWriteBody() throws Exception {
    mockMvc.perform(post("/api/dsl/files/dsl/LoanDsl.java")
            .contentType(MediaType.TEXT_PLAIN)
            .content("raw content"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.content").value("raw content"));

    mockMvc.perform(post("/api/dsl/files/flush"))
            .andExpect(status().isOk());
  }

  @Test
  void listsFilesWithPrefix() throws Exception {
    mockMvc.perform(post("/api/dsl/files/bulk")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"files":[
                      {"path":"dsl/LoanDsl.java","content":"class LoanDsl {}"},
                      {"path":"models/ModelDsl.java","content":"class ModelDsl {}"}
                    ]}
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.staged").value(2))
            .andExpect(jsonPath("$.failed").value(0));

    mockMvc.perform(post("/api/dsl/files/flush"))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/dsl/files"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));

    mockMvc.perform(get("/api/dsl/files").param("prefix", "models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].path").value("models/ModelDsl.java"));
  }

  @Test
  void reportsExistsAndMissingFiles() throws Exception {
    mockMvc.perform(get("/api/dsl/files/exists/dsl/LoanDsl.java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(false));

    mockMvc.perform(get("/api/dsl/files/dsl/MissingDsl.java"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void rejectsEscapingPaths() throws Exception {
    mockMvc.perform(get("/api/dsl/files/../secret.txt"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}

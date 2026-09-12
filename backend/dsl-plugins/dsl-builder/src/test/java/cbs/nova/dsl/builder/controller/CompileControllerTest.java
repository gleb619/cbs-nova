package cbs.nova.dsl.builder.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.service.BuilderWorkQueue;
import cbs.nova.dsl.builder.service.CompileService;
import cbs.nova.dsl.builder.service.GitService;
import cbs.nova.dsl.builder.service.GradleService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CompileController.class)
@Import({CompileService.class, GitService.class, GradleService.class, BuilderWorkQueue.class,
    CompileControllerTest.Config.class})
class CompileControllerTest {

  private static final Path WORKSPACE = Path.of(System.getProperty("java.io.tmpdir"),
          "dsl-builder-mvc-test");

  private static final String SAMPLE_SOURCE = """
          import cbs.nova.dsl.*;
          import java.util.List;

          List<DslObject> define() {
            return Dsl.process("SampleProcess")
                .input(String.class)
                .output(String.class)
                .execute(ctx -> Result.success("Hello from DSL: " + ctx.body()))
                .buildList();
          }
          """;

  private static final String BROKEN_SOURCE = """
          import cbs.nova.dsl.*;
          import java.util.List;

          List<DslObject> define() {
            return Dsl.process("SampleProcess")
                .input(String.class)
                .nonExistentStep()
                .buildList();
          }
          """;

  @Autowired
  MockMvc mockMvc;

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
  void compilesValidRequest() throws Exception {
    mockMvc.perform(post("/api/dsl/compile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody(SAMPLE_SOURCE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.generatedFiles").isNotEmpty())
            .andExpect(jsonPath("$.id").isString());
  }

  @Test
  void rejectsInvalidPackageName() throws Exception {
    var body = """
            {"targetPackage":"1bad-pkg","sources":{"dsl/SampleDsl.java":%s}}
            """.formatted(toJson(SAMPLE_SOURCE));

    mockMvc.perform(post("/api/dsl/compile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
  }

  @Test
  void reportsCompileFailure() throws Exception {
    mockMvc.perform(post("/api/dsl/compile")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody(BROKEN_SOURCE)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error").value("COMPILE_FAILED"))
            .andExpect(jsonPath("$.diagnostics").isNotEmpty());
  }

  @Test
  void downloadsUnknownSessionAsNotFound() throws Exception {
    mockMvc.perform(get("/api/dsl/compile/{id}/download", "missing"))
            .andExpect(status().isNotFound());
  }

  private String jsonBody(String source) {
    return """
            {"buildVersion":"v1","sources":{"dsl/SampleDsl.java":%s}}
            """.formatted(toJson(source));
  }

  private String toJson(String source) {
    return "\""
            + source
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
            + "\"";
  }
}

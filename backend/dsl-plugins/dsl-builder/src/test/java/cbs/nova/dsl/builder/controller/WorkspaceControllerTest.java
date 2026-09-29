package cbs.nova.dsl.builder.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.config.WorkspaceMode;
import cbs.nova.dsl.builder.service.GitService;
import cbs.nova.dsl.builder.service.WorkspaceService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkspaceController.class)
@Import({WorkspaceService.class, GitService.class, WorkspaceControllerTest.Config.class})
class WorkspaceControllerTest {

  private static final Path WORKSPACE = Path.of(System.getProperty("java.io.tmpdir"),
          "dsl-builder-workspace-mvc-" + System.nanoTime());

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
              WorkspaceMode.SHARED,
              null,
              null,
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
  void workspaceEndpointReportsModeAndWorkTree() throws Exception {
    mockMvc.perform(get("/api/dsl/workspace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("SHARED"))
            .andExpect(jsonPath("$.workTree").value(
                    WORKSPACE.toAbsolutePath().normalize().toString().replace('\\', '/')));
  }
}

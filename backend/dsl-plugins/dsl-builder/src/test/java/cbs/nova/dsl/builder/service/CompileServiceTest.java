package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.CompileModels.CompileRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class CompileServiceTest {

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

  @TempDir
  Path workspaceDir;

  private CompileService service() {
    var properties = new DslBuilderProperties(
            workspaceDir,
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.1-SNAPSHOT",
            "1.27.0",
            "v1",
            List.of("clean", "build"),
            List.of("dsl", "models"),
            "project/templates",
            null,
            null,
            null,
            null,
            null,
            null);
    var gradleService = new GradleService(properties);
    var workQueue = new BuilderWorkQueue(properties);
    workQueue.start();
    var service = new CompileService(
            properties, new GitService(), gradleService, new DefaultResourceLoader(), workQueue);
    service.loadTemplates();
    return service;
  }

  @Test
  void compilesMinimalDslSourceThroughGradle() {
    var request = new CompileRequest(
            "v1", null, null, null, null, null, Map.of("dsl/SampleDsl.java", SAMPLE_SOURCE));

    var result = service().compile(request);

    assertThat(result.success()).isTrue();
    assertThat(result.id()).isNotBlank();
    assertThat(result.generatedFiles())
            .anyMatch(path -> path.endsWith("ProcessWorkflow.java"))
            .anyMatch(path -> path.endsWith("ProcessDefinition.java"));
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void rejectsInvalidPackageName() {
    var request = new CompileRequest(
            "v1", "1bad-pkg", null, null, null, null, Map.of("dsl/SampleDsl.java", SAMPLE_SOURCE));

    assertThatThrownBy(() -> service().compile(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("targetPackage");
  }

  @Test
  void rejectsRequestWithoutSourcesOrRepo() {
    var request = new CompileRequest("v1", null, null, null, null, null, null);

    assertThatThrownBy(() -> service().compile(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sources or repoUrl");
  }
}

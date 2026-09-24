package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.model.CompileModels.CompileRequest;
import java.nio.file.Files;
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
    return service(properties(null));
  }

  private CompileService service(DslBuilderProperties properties) {
    var gradleService = new GradleService(properties);
    var workQueue = new BuilderWorkQueue(properties);
    workQueue.start();
    var service = new CompileService(
            properties, new GitService(), gradleService, new DefaultResourceLoader(), workQueue);
    service.loadTemplates();
    return service;
  }

  private DslBuilderProperties properties(DslBuilderProperties.Git git) {
    return new DslBuilderProperties(
            workspaceDir,
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
            git,
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
  void compilesDslWithVersionedModelImportThroughGradle() {
    var dsl = """
            import cbs.nova.dsl.*;
            import com.example.dslbuild.v1.SampleModels.*;
            import java.util.List;

            List<DslObject> define() {
              return Dsl.process("SampleProcess")
                  .input(SampleIn.class)
                  .output(String.class)
                  .execute(ctx -> Result.success("Hello from DSL: " + ctx.body()))
                  .buildList();
            }
            """;
    var models = """
            public class SampleModels {
              public record SampleIn(String value) {}
            }
            """;
    var request = new CompileRequest(
            "v1", "com.example.dslbuild", null, null, null, null,
            Map.of("dsl/SampleDsl.java", dsl, "models/SampleModels.java", models));

    var result = service().compile(request);

    assertThat(result.success()).isTrue();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.generatedFiles())
            .anyMatch(path -> path.endsWith("SampleModels.java"));
  }

  @Test
  void rejectsRequestRepoUrlBeforeCloning() {
    var request = new CompileRequest(
            "v1", null, null, null, "file:///etc/passwd", null,
            Map.of("dsl/SampleDsl.java", SAMPLE_SOURCE));

    assertThatThrownBy(() -> service().compile(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("repoUrl");
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
            .hasMessageContaining("sources or a git repository");
  }

  @Test
  void compilesFromConfiguredGitRepository() throws Exception {
    var origin = createRepositoryWithCommit();
    var git = new DslBuilderProperties.Git(
            true, null, null, origin.toString(), null, "main", 5, false, "origin");
    var request = new CompileRequest("v1", null, null, null, null, null, null);

    var result = service(properties(git)).compile(request);

    assertThat(result.success()).isTrue();
    assertThat(result.generatedFiles())
            .anyMatch(path -> path.endsWith("ProcessWorkflow.java"));
  }

  private Path createRepositoryWithCommit() throws Exception {
    var repoDir = workspaceDir.resolve("origin");
    try (var git = org.eclipse.jgit.api.Git.init()
            .setDirectory(repoDir.toFile())
            .setInitialBranch("main")
            .call()) {
      var dslDir = repoDir.resolve("dsl");
      Files.createDirectories(dslDir);
      Files.writeString(dslDir.resolve("SampleDsl.java"), SAMPLE_SOURCE);
      git.add().addFilepattern(".").call();
      git.commit()
              .setMessage("initial")
              .setAuthor("dsl-builder-test", "test@example.com")
              .setCommitter("dsl-builder-test", "test@example.com")
              .call();
    }
    return repoDir;
  }
}

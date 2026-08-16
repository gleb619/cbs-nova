package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class CompilingDslDefinitionLoaderTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @TempDir
  Path tempDir;

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void loadCompilesJavaSourcesAndRegistersDefinitions() throws Exception {
    var source = """
            List<DslObject> define() {
              return Dsl.process("CompiledProcess")
                      .input(String.class)
                      .output(String.class)
                      .execute(ctx -> Result.success("compiled"))
                      .buildList();
            }
            """;
    Files.writeString(tempDir.resolve("CompiledProcess.java"), source);

    var gm = GlobalManager.globalManager();
    new CompilingDslDefinitionLoader().load(tempDir, gm);

    assertThat(gm.hasProcess("CompiledProcess")).isTrue();
    var ctx = contextFactory.of("test", ExecutionMode.PREVIEW);
    var result = gm.runProcess("CompiledProcess", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("compiled");
  }

  @Test
  void loadEmptySourceDirectoryFallsBackToSpiAndDoesNotThrow() {
    var gm = GlobalManager.globalManager();

    assertThatCode(() -> new CompilingDslDefinitionLoader().load(tempDir, gm))
            .doesNotThrowAnyException();

    assertThat(gm.hasProcess("SpiLoadedProcess")).isTrue();
  }

  @Test
  void loadIgnoresCompileErrorsAndDoesNotThrow() throws Exception {
    var source = """
            List<DslObject> define() {
              return Dsl.process("BrokenProcess")
                      .input(String.class)
                      .output(String.class)
                      .execute(ctx -> Result.success("broken"))
                      buildList();
            }
            """;
    Files.writeString(tempDir.resolve("BrokenProcess.java"), source);

    var gm = GlobalManager.globalManager();

    assertThatCode(() -> new CompilingDslDefinitionLoader().load(tempDir, gm))
            .doesNotThrowAnyException();

    assertThat(gm.hasProcess("BrokenProcess")).isFalse();
  }
}

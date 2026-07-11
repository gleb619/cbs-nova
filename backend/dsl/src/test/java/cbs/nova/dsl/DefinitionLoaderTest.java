package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class DefinitionLoaderTest {

  private final ContextFactory contextFactory = new ContextFactory();
  @TempDir
  Path tempDir;

  @BeforeEach
  void reset() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void loadsProcessFromCompactSourceFile() throws Exception {
    var source = tempDir.resolve("TestProcess.java");
    Files.writeString(
            source,
            """
                    import cbs.nova.dsl.*;
                    import java.util.List;

                    void main() {}

                    List<DslObject> define() {
                      return Dsl.process("LoadedProcess")
                          .input(String.class)
                          .output(String.class)
                          .execute(ctx -> Result.success("loaded"))
                          .buildList();
                    }
                    """);

    var gm = GlobalManager.getInstance();
    new DefinitionLoader().load(tempDir, gm);

    var ctx = contextFactory.of("test", ExecutionMode.PREVIEW);
    var result = gm.runProcess("LoadedProcess", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("loaded");
  }

  @Test
  void skipsFileThatFailsToCompile() throws Exception {
    var bad = tempDir.resolve("BadProcess.java");
    Files.writeString(bad, "this is not valid java !!!;");

    var gm = GlobalManager.getInstance();
    assertThatCode(() -> new DefinitionLoader().load(tempDir, gm)).doesNotThrowAnyException();
  }
}

package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

class DslSourceCompilerTest {

  private DslSourceCompiler compiler() {
    return CompileConfig.compileConfig().dslSourceCompiler();
  }

  @Test
  void compilesAndLoadsValidProcess(@TempDir Path srcDir) throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve("dsl"));
    Files.writeString(
            dslDir.resolve("GoodProcess.java"),
            """
                    import cbs.nova.dsl.*;
                    import java.util.List;

                    void main() {}

                    List<DslObject> define() {
                      return Dsl.process("GoodProcess")
                          .input(String.class)
                          .output(String.class)
                          .execute(ctx -> Result.success("ok"))
                          .buildList();
                    }
                    """);

    var outDir = Files.createTempDirectory("dsl-codegen-test-");
    var objects = compiler().compileAndLoad(srcDir, outDir,
            new SourceCompiler.CompileOptions("demo", null, Level.INFO));
    assertThat(objects).hasSize(1);
    assertThat(objects.get(0).name()).isEqualTo("GoodProcess");
    assertThat(objects.get(0).type().name()).isEqualTo("PROCESS");
  }

  @Test
  void ignoresFilesThatFailToCompile(@TempDir Path srcDir) {
    try {
      var dslDir = Files.createDirectories(srcDir.resolve("dsl"));
      Files.writeString(dslDir.resolve("BadFile.java"), "this is not valid java !!!;");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertThatCode(() -> {
      var outDir = Files.createTempDirectory("dsl-codegen-test-");
      compiler().compileAndLoad(srcDir, outDir,
              new SourceCompiler.CompileOptions("demo", null, Level.INFO));
    }).doesNotThrowAnyException();
  }

  @Test
  void emptySourceDirReturnsEmptyList(@TempDir Path srcDir) throws Exception {
    var outDir = Files.createTempDirectory("dsl-codegen-test-");
    var objects = compiler().compileAndLoad(srcDir, outDir,
            new SourceCompiler.CompileOptions("demo", null, Level.INFO));
    assertThat(objects).isEmpty();
  }
}

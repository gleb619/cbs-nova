package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class DslSourceCompilerTest {

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

    var objects = new DslSourceCompiler().compileAndLoad(srcDir);
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
    assertThatCode(() -> new DslSourceCompiler().compileAndLoad(srcDir))
            .doesNotThrowAnyException();
  }

  @Test
  void emptySourceDirReturnsEmptyList(@TempDir Path srcDir) throws Exception {
    var objects = new DslSourceCompiler().compileAndLoad(srcDir);
    assertThat(objects).isEmpty();
  }
}

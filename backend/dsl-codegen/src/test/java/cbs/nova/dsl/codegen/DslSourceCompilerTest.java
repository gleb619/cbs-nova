package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

import java.nio.file.Files;
import java.nio.file.Path;

class DslSourceCompilerTest {

  private DslSourceCompiler compiler() {
    return CompileConfig.compileConfig().dslSourceCompiler();
  }

  @Test
  void compilesAndLoadsValidProcess(@TempDir Path srcDir) throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
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
      var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
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

  @Test
  void writesPreprocessedSourcesUnderPackageTreeWhenTargetPackageSet(@TempDir Path srcDir)
          throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
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

    var modelsDir = Files.createDirectories(srcDir.resolve(CompilerConstants.MODELS_FOLDER));
    Files.writeString(
            modelsDir.resolve("GoodModel.java"),
            """
                    public class GoodModel {
                      private String value;
                      public String getValue() { return value; }
                      public void setValue(String value) { this.value = value; }
                    }
                    """);

    var outDir = Files.createTempDirectory("dsl-codegen-test-");
    var objects = compiler().compileAndLoad(srcDir, outDir,
            new SourceCompiler.CompileOptions("demo", "cbs.nova.dsl.generated", Level.INFO));

    assertThat(objects).hasSize(1);

    var expectedDsl = outDir.resolve("cbs/nova/dsl/generated/GoodProcess.java");
    var expectedModel = outDir.resolve("cbs/nova/dsl/generated/GoodModel.java");
    assertThat(expectedDsl).exists();
    assertThat(expectedModel).exists();
    assertThat(Files.readString(expectedDsl)).contains("package cbs.nova.dsl.generated;");
    assertThat(Files.readString(expectedModel)).contains("package cbs.nova.dsl.generated;");
  }

}

package cbs.nova.dsl.codegen;

import static cbs.nova.dsl.codegen.CompilerConstants.DEFAULT_BUILD_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.model.DslCompilerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

import java.nio.file.Files;
import java.nio.file.Path;

class DslCompilerTest {

  @TempDir
  Path srcDir;
  @TempDir
  Path outDir;

  @Test
  void compilesProcessDslAndWritesFiles() throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
    Files.writeString(
            dslDir.resolve("TestProcess.java"),
            """
                    import cbs.nova.dsl.*;
                    import java.util.List;

                    void main() {}

                    List<DslObject> define() {
                      return Dsl.process("LoanDisbursement")
                          .input(String.class)
                          .output(String.class)
                          .execute(ctx -> Result.success("ok"))
                          .buildList();
                    }
                    """);

    var options = DslCompilerOptions.builder()
        .srcDir(srcDir)
        .outputDir(outDir)
        .buildVersion(DEFAULT_BUILD_VERSION)
        .targetPackage(null)
        .logLevel(Level.INFO)
        .classpath(null)
        .useFileNameSubPackage(true)
        .build();
    DslCompiler.compile(options);

    var dir = outDir.resolve("cbs/nova/dsl/generated/loandisbursement/v1");
    assertThat(dir.resolve("LoanDisbursementProcessWorkflow.java")).exists();
    assertThat(dir.resolve("LoanDisbursementProcessDefinition.java")).exists();
    assertThat(dir.resolve("LoanDisbursementGeneratedClassProvider.java")).exists();
    assertThat(Files.readString(dir.resolve("LoanDisbursementGeneratedClassProvider.java")))
            .contains("executeJson()")
            .contains("LambdaExpr");
  }

  @Test
  void compilesTransactionDslAndWritesFiles() throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
    Files.writeString(
            dslDir.resolve("TestTx.java"),
            """
                    import cbs.nova.dsl.*;
                    import java.util.List;

                    void main() {}

                    List<DslObject> define() {
                      return Dsl.transaction("KycCheck")
                          .input(String.class)
                          .output(String.class)
                          .execute(ctx -> Result.success("ok"))
                          .buildList();
                    }
                    """);

    var options = DslCompilerOptions.builder()
        .srcDir(srcDir)
        .outputDir(outDir)
        .buildVersion(DEFAULT_BUILD_VERSION)
        .targetPackage(null)
        .logLevel(Level.INFO)
        .classpath(null)
        .useFileNameSubPackage(true)
        .build();
    DslCompiler.compile(options);


    var dir = outDir.resolve("cbs/nova/dsl/generated/kyccheck/v1");
    assertThat(dir.resolve("KycCheckTransactionActivity.java")).exists();
    assertThat(dir.resolve("KycCheckTransactionDefinition.java")).exists();
    assertThat(dir.resolve("KycCheckGeneratedClassProvider.java")).exists();
    assertThat(Files.readString(dir.resolve("KycCheckGeneratedClassProvider.java")))
            .contains("executeJson()")
            .contains("LambdaExpr");
  }

  @Test
  void usesBuildVersionAndTargetPackage() throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
    var modelsDir = Files.createDirectories(srcDir.resolve(CompilerConstants.MODELS_FOLDER));
    Files.writeString(
            modelsDir.resolve("TestModels.java"),
            """
                    public class TestModels {
                      public record TestIn(String value) {}
                    }
                    """);
    Files.writeString(
            dslDir.resolve("TestProcess.java"),
            """
                    import cbs.nova.dsl.*;
                    import cbs.nova.dsl.codegen.test.TestModels.*;
                    import java.util.List;

                    void main() {}

                    List<DslObject> define() {
                      return Dsl.process("VersionedProcess")
                          .input(TestIn.class)
                          .output(String.class)
                          .execute(ctx -> Result.success("ok"))
                          .buildList();
                    }
                    """);

    var options = DslCompilerOptions.builder()
        .srcDir(srcDir)
        .outputDir(outDir)
        .buildVersion("abc1234")
        .targetPackage("cbs.nova.dsl.codegen.test")
        .logLevel(Level.INFO)
        .classpath(null)
        .useFileNameSubPackage(true)
        .build();
    DslCompiler.compile(options);


    assertThat(outDir.resolve("cbs/nova/dsl/codegen/test/testprocess/abc1234/TestModels.class"))
            .exists();
    assertThat(outDir.resolve("cbs/nova/dsl/codegen/test/testprocess/abc1234/TestProcess.class"))
            .exists();
    assertThat(outDir.resolve("cbs/nova/dsl/codegen/test/GeneratedDslDefinitionProvider.class"))
            .exists();

    var preprocessedDslDir = outDir.resolve("cbs/nova/dsl/codegen/test/testprocess/abc1234");
    assertThat(preprocessedDslDir.resolve("TestProcess.java")).exists();
    var generatedDir = outDir.resolve("cbs/nova/dsl/codegen/test/versionedprocess/abc1234");
    assertThat(generatedDir.resolve("VersionedProcessProcessWorkflow.java")).exists();
    assertThat(generatedDir.resolve("VersionedProcessProcessDefinition.java")).exists();
    assertThat(Files.readString(generatedDir.resolve("VersionedProcessProcessDefinition.java")))
            .contains("VERSION = \"abc1234\"");
    assertThat(
            Files.readString(generatedDir.resolve("VersionedProcessGeneratedClassProvider.java")))
            .contains("\"abc1234\"");
  }

  @Test
  void compilesParameterBasedProcessWithMapInput() throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
    Files.writeString(
            dslDir.resolve("ParamProcess.java"),
            """
                    import cbs.nova.dsl.*;
                    import java.util.List;

                    void main() {}

                    List<DslObject> define() {
                      return Dsl.process("ParamProcess")
                          .parameters(reg -> reg.string("name"))
                          .execute(ctx -> {
                            MapInput body = ctx.body();
                            return Result.success("hello " + body.values().get("name"));
                          })
                          .buildList();
                    }
                    """);

    var options = DslCompilerOptions.builder()
        .srcDir(srcDir)
        .outputDir(outDir)
        .buildVersion(DEFAULT_BUILD_VERSION)
        .targetPackage(null)
        .logLevel(Level.INFO)
        .classpath(null)
        .useFileNameSubPackage(true)
        .build();
    DslCompiler.compile(options);


    var dir = outDir.resolve("cbs/nova/dsl/generated/paramprocess/v1");
    assertThat(dir.resolve("ParamProcessProcessWorkflow.java")).exists();
    assertThat(dir.resolve("ParamProcessProcessDefinition.java")).exists();
  }

  @Test
  void placesAllGeneratedSourcesInFlatVersionedPackageWhenSubPackageDisabled() throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve(CompilerConstants.DSL_FOLDER));
    var modelsDir = Files.createDirectories(srcDir.resolve(CompilerConstants.MODELS_FOLDER));
    Files.writeString(
            modelsDir.resolve("TestModels.java"),
            """
                    public class TestModels {
                      public record TestIn(String value) {}
                    }
                    """);
    Files.writeString(
            dslDir.resolve("TestProcess.java"),
            """
                    import cbs.nova.dsl.*;
                    import cbs.nova.dsl.test.TestModels.*;
                    import java.util.List;

                    void main() {}

                    List<DslObject> define() {
                      return Dsl.process("FlatProcess")
                          .input(TestIn.class)
                          .output(String.class)
                          .execute(ctx -> Result.success("ok"))
                          .buildList();
                    }
                    """);

    var options = new DslCompilerOptions(
            srcDir, outDir, "v1", "cbs.nova.dsl.test", Level.INFO, null, false);
    DslCompiler.compile(options);


    var flatDir = outDir.resolve("cbs/nova/dsl/test/v1");
    assertThat(flatDir.resolve("TestProcess.java")).exists();
    assertThat(flatDir.resolve("TestModels.java")).exists();
    assertThat(flatDir.resolve("FlatProcessProcessWorkflow.java")).exists();
    assertThat(flatDir.resolve("FlatProcessProcessDefinition.java")).exists();
    assertThat(flatDir.resolve("FlatProcessGeneratedClassProvider.java")).exists();
    assertThat(Files.readString(flatDir.resolve("TestProcess.java")))
            .contains("package cbs.nova.dsl.test.v1;");
    assertThat(Files.readString(flatDir.resolve("FlatProcessProcessDefinition.java")))
            .contains("VERSION = \"v1\"");
  }
}

package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    DslCompiler.compile(srcDir, outDir);

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

    DslCompiler.compile(srcDir, outDir);

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

    DslCompiler.compile(srcDir, outDir, "abc1234", "cbs.nova.dsl.codegen.test");

    assertThat(outDir.resolve("cbs/nova/dsl/codegen/test/TestModels.class")).exists();
    assertThat(outDir.resolve("cbs/nova/dsl/codegen/test/TestProcess.class")).exists();
    assertThat(outDir.resolve("cbs/nova/dsl/codegen/test/GeneratedDslDefinitionProvider.class"))
            .exists();

    var dir = outDir.resolve("cbs/nova/dsl/codegen/test/versionedprocess/abc1234");
    assertThat(dir.resolve("VersionedProcessProcessWorkflow.java")).exists();
    assertThat(dir.resolve("VersionedProcessProcessDefinition.java")).exists();
    assertThat(Files.readString(dir.resolve("VersionedProcessProcessDefinition.java")))
            .contains("VERSION = \"abc1234\"");
    assertThat(Files.readString(dir.resolve("VersionedProcessGeneratedClassProvider.java")))
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

    DslCompiler.compile(srcDir, outDir);

    var dir = outDir.resolve("cbs/nova/dsl/generated/paramprocess/v1");
    assertThat(dir.resolve("ParamProcessProcessWorkflow.java")).exists();
    assertThat(dir.resolve("ParamProcessProcessDefinition.java")).exists();
  }
}

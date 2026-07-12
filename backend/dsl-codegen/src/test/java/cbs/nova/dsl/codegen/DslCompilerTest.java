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
    var dslDir = Files.createDirectories(srcDir.resolve("dsl"));
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
  }

  @Test
  void compilesTransactionDslAndWritesFiles() throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve("dsl"));
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
  }

  @Test
  void usesBuildVersionAndTargetPackage() throws Exception {
    var dslDir = Files.createDirectories(srcDir.resolve("dsl"));
    var modelsDir = Files.createDirectories(srcDir.resolve("models"));
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

    var dir = outDir.resolve("cbs/nova/dsl/generated/versionedprocess/v1");
    assertThat(dir.resolve("VersionedProcessProcessWorkflow.java")).exists();
    assertThat(dir.resolve("VersionedProcessProcessDefinition.java")).exists();
    assertThat(Files.readString(dir.resolve("VersionedProcessProcessDefinition.java")))
            .contains("VERSION = \"abc1234\"");
  }
}

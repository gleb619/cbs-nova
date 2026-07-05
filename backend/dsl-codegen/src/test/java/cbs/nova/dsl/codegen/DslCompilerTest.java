package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class DslCompilerTest {
  @TempDir
  Path srcDir;
  @TempDir
  Path outDir;

  @Test
  void compilesProcessDslAndWritesFiles() throws Exception {
    Files.writeString(
            srcDir.resolve("TestProcess.java"),
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
    Files.writeString(
            srcDir.resolve("TestTx.java"),
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
}

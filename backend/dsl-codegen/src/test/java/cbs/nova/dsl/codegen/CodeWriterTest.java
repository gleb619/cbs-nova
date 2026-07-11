package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class CodeWriterTest {

  @TempDir
  Path tempDir;

  @Test
  void writesFilesInCorrectDirectoryTree() throws Exception {
    var source = new GeneratedSource("cbs.nova.dsl.generated.loan.v1", "LoanProcessWorkflow",
            "// source");
    CodeWriter.write(List.of(source), tempDir);

    var expectedFile = tempDir.resolve("cbs/nova/dsl/generated/loan/v1/LoanProcessWorkflow.java");
    assertThat(expectedFile).exists();
    assertThat(Files.readString(expectedFile)).isEqualTo("// source");
  }

  @Test
  void writesMultipleSourcesToSameDir() throws Exception {
    var s1 = new GeneratedSource("cbs.nova.dsl.generated.loan.v1", "LoanProcessWorkflow",
            "// iface");
    var s2 = new GeneratedSource("cbs.nova.dsl.generated.loan.v1", "LoanProcessDefinition",
            "// impl");
    CodeWriter.write(List.of(s1, s2), tempDir);

    var dir = tempDir.resolve("cbs/nova/dsl/generated/loan/v1");
    assertThat(dir.resolve("LoanProcessWorkflow.java")).exists();
    assertThat(dir.resolve("LoanProcessDefinition.java")).exists();
  }
}

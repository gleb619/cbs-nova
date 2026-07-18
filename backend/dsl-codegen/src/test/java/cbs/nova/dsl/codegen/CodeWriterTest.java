package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.model.GeneratedSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeWriterTest {

  @TempDir
  Path tempDir;

  @Test
  void writesFilesInCorrectDirectoryTree() throws Exception {
    var writer = new CodeWriter();
    var source = new GeneratedSource("cbs.nova.dsl.generated.loan.v1", "LoanProcessWorkflow",
            "// source");
    writer.write(List.of(source), tempDir);

    var expectedFile = tempDir.resolve("cbs/nova/dsl/generated/loan/v1/LoanProcessWorkflow.java");
    assertThat(expectedFile).exists();
    assertThat(Files.readString(expectedFile)).isEqualTo("// source");
  }

  @Test
  void writesMultipleSourcesToSameDir() throws Exception {
    var writer = new CodeWriter();
    var s1 = new GeneratedSource("cbs.nova.dsl.generated.loan.v1", "LoanProcessWorkflow",
            "// iface");
    var s2 = new GeneratedSource("cbs.nova.dsl.generated.loan.v1", "LoanProcessDefinition",
            "// impl");
    writer.write(List.of(s1, s2), tempDir);

    var dir = tempDir.resolve("cbs/nova/dsl/generated/loan/v1");
    assertThat(dir.resolve("LoanProcessWorkflow.java")).exists();
    assertThat(dir.resolve("LoanProcessDefinition.java")).exists();
  }

  @Test
  void writesRawFileAndCreatesParentDirectories() throws Exception {
    var writer = new CodeWriter();
    var file = tempDir.resolve("a/b/c/Test.java");
    writer.write(file, "hello");

    assertThat(file).exists();
    assertThat(Files.readString(file)).isEqualTo("hello");
  }

  @Test
  void createsNestedDirectories() throws Exception {
    var writer = new CodeWriter();
    var dir = tempDir.resolve("x/y/z");
    writer.createDirectories(dir);

    assertThat(dir).exists().isDirectory();
  }
}

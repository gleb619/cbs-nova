package cbs.nova.dsl.codegen.task;

import static cbs.nova.dsl.codegen.CompileConfig.DEFAULT_GENERATED_BASE_PACKAGE;
import static cbs.nova.dsl.codegen.CompilerConstants.DEFAULT_BUILD_VERSION;
import static cbs.nova.dsl.codegen.CompilerConstants.DSL_FOLDER;
import static cbs.nova.dsl.codegen.CompilerConstants.MODELS_FOLDER;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.model.DslCompilerOptions;
import cbs.nova.dsl.codegen.preprocessor.DslPreprocessor;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import cbs.nova.dsl.codegen.util.ModelImportResolver;
import cbs.nova.dsl.codegen.util.SourcePackageResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PreprocessSourcesTaskTest {

  private static final String TARGET_PACKAGE = "cbs.nova.dsl.test";

  @TempDir
  Path srcDir;
  @TempDir
  Path outDir;

  private final CodegenNaming codegenNaming = new CodegenNaming(DEFAULT_GENERATED_BASE_PACKAGE);
  private final SourcePackageResolver sourcePackageResolver = new SourcePackageResolver(
          new DslPackageNameResolver(codegenNaming), new ModelImportResolver(codegenNaming));
  private final PreprocessSourcesTask task = new PreprocessSourcesTask(new DslPreprocessor(),
          codegenNaming, sourcePackageResolver);

  @Test
  void nameIsPreprocess() {
    assertThat(task.name()).isEqualTo("preprocess");
  }

  @Test
  void missingDslFolderYieldsEmptyPreprocessedSources() throws IOException {
    var context = task.run(context(null, true));

    assertThat(context.preprocessedSources()).isEmpty();
  }

  @Test
  void invalidSourceIsSkippedWithoutFailingTheRun() throws IOException {
    var dslDir = Files.createDirectories(srcDir.resolve(DSL_FOLDER));
    Files.writeString(dslDir.resolve("GoodProcess.java"), validDsl("LoanDisbursement"));
    Files.writeString(dslDir.resolve("BrokenProcess.java"), "package broken;\n\nvoid main() {}\n");

    var context = task.run(context(null, true));

    assertThat(context.preprocessedSources()).hasSize(1);
    assertThat(context.preprocessedSources().getFirst())
            .contains("public class GoodProcess implements DslCompactSource")
            .doesNotContain("BrokenProcess");
  }

  @Test
  void ignoresNonJavaFilesAndWalksNestedFolders() throws IOException {
    var dslDir = Files.createDirectories(srcDir.resolve(DSL_FOLDER));
    Files.writeString(dslDir.resolve("TopProcess.java"), validDsl("TopProcess"));
    Files.writeString(dslDir.resolve("notes.txt"), "not a source file");
    var nestedDir = Files.createDirectories(dslDir.resolve("nested"));
    Files.writeString(nestedDir.resolve("NestedProcess.java"), validDsl("NestedProcess"));

    var context = task.run(context(null, true));

    assertThat(context.preprocessedSources()).hasSize(2);
    assertThat(context.preprocessedSources())
            .anyMatch(source -> source
                    .contains("public class TopProcess implements DslCompactSource"))
            .anyMatch(source -> source.contains(
                    "public class NestedProcess implements DslCompactSource"));
  }

  @Test
  void rewritesModelImportsWhenModelsFolderPresent() throws IOException {
    var dslDir = Files.createDirectories(srcDir.resolve(DSL_FOLDER));
    var modelsDir = Files.createDirectories(srcDir.resolve(MODELS_FOLDER));
    Files.writeString(modelsDir.resolve("TestModels.java"),
            "public class TestModels {\n  public record TestIn(String value) {}\n}\n");
    Files.writeString(dslDir.resolve("FlatProcess.java"), validDsl("FlatProcess",
            "import cbs.nova.dsl.test.TestModels.*;", "TestIn.class"));

    var context = task.run(context(TARGET_PACKAGE, false));

    assertThat(context.preprocessedSources()).hasSize(1);
    assertThat(context.preprocessedSources().getFirst())
            .contains("import cbs.nova.dsl.test.v1.TestModels.*;")
            .contains("package cbs.nova.dsl.test.v1;");
  }

  @Test
  void runsWithoutModelsFolder() throws IOException {
    var dslDir = Files.createDirectories(srcDir.resolve(DSL_FOLDER));
    Files.writeString(dslDir.resolve("FlatProcess.java"), validDsl("FlatProcess"));

    var context = task.run(context(TARGET_PACKAGE, false));

    assertThat(context.preprocessedSources()).hasSize(1);
    assertThat(context.preprocessedSources().getFirst())
            .contains("package cbs.nova.dsl.test.v1;")
            .contains("public class FlatProcess implements DslCompactSource");
  }

  private CompileContext context(String targetPackage, boolean useFileNameSubPackage) {
    var options = new DslCompilerOptions(
            srcDir, outDir, DEFAULT_BUILD_VERSION, targetPackage, null, null,
            useFileNameSubPackage, null);
    return CompileContext.create(options);
  }

  private static String validDsl(String name, String... extraImportsAndInput) {
    var imports = new StringBuilder("import cbs.nova.dsl.*;\n");
    var input = "String.class";
    if (extraImportsAndInput.length > 0) {
      imports.append(extraImportsAndInput[0]).append("\n");
      input = extraImportsAndInput[1];
    }
    return """
            %s
            import java.util.List;

            void main() {}

            List<DslObject> define() {
              return Dsl.process("%s")
                  .input(%s)
                  .output(String.class)
                  .execute(ctx -> Result.success("ok"))
                  .buildList();
            }
            """.formatted(imports.toString().stripTrailing(), name, input);
  }
}

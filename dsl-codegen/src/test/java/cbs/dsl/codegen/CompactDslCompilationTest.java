package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

class CompactDslCompilationTest {

  private static final Path DSL_EXAMPLES_DIR = Path.of("../dsl-examples/src");

  static Stream<Path> dslFiles() throws IOException {
    try (Stream<Path> files = Files.list(DSL_EXAMPLES_DIR)) {
      return files.filter(p -> p.toString().endsWith(".java")).sorted().toList().stream();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dslFiles")
  @DisplayName("Should parse each DSL file and generate valid Java wrapper")
  void shouldParseDslAndGenerateValidWrapper(Path dslFile) throws IOException {
    String content = Files.readString(dslFile);

    // Parse the compact DSL
    DslCompiler.ParsedDsl parsed = DslCompiler.parseCompactDsl(content);

    assertNotNull(parsed);
    assertFalse(parsed.body().isEmpty(), "Body should not be empty for " + dslFile);
    assertTrue(
        parsed.body().contains("return"), "Body should contain return statement in " + dslFile);

    // Generate wrapper class
    String className = dslFile.getFileName().toString().replace(".java", "");
    String importsBlock = parsed.imports().isEmpty() ? "" : parsed.imports() + "\n";
    String wrapper = Substitutor.format(
        DslCompiler.WRAPPER_TEMPLATE,
        Map.of("className", className, "body", parsed.body(), "imports", importsBlock));

    // Verify the wrapper is valid Java
    CompilationUnit cu = StaticJavaParser.parse(wrapper);
    assertTrue(cu.getTypes().size() > 0, "Wrapper should have at least one type declaration");

    String wrapperSource = cu.toString();
    assertTrue(wrapperSource.contains("define()"), "Wrapper should contain define method");
  }

  @Test
  @DisplayName("Should find DSL example files")
  void shouldFindDslExampleFiles() throws IOException {
    assertTrue(Files.isDirectory(DSL_EXAMPLES_DIR), "DSL examples directory should exist");
    long count;
    try (Stream<Path> files = Files.list(DSL_EXAMPLES_DIR)) {
      count = files.filter(p -> p.toString().endsWith(".java")).count();
    }
    assertTrue(count >= 10, "Should have at least 10 DSL example files, found: " + count);
  }
}

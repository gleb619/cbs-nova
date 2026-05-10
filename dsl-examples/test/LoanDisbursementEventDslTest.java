import static org.junit.jupiter.api.Assertions.*;

import cbs.dsl.codegen.DslCompiler;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class LoanDisbursementEventDslTest {

  private static final Path OUTPUT = Path.of("build/dsl-classes");

  @Test @DisplayName("should parse DSL source as valid implicit class")
  void shouldParseDslSourceAsValidImplicitClass() throws Exception {
    Path source = Path.of("src/LoanDisbursementEventDsl.java");
    String content = Files.readString(source);
    assertFalse(DslCompiler.containsExplicitTypeDeclaration(content));
    DslCompiler.ParsedDsl parsed = DslCompiler.parseCompactDsl(content);
    assertFalse(parsed.body().isEmpty());
    assertTrue(parsed.body().contains("LOAN_DISBURSEMENT"));
  }

  @Test @DisplayName("should generate compiled class file")
  void shouldGenerateCompiledClassFile() {
    assertTrue(Files.exists(OUTPUT.resolve("LoanDisbursementEventDsl.class")));
  }

  @Test @DisplayName("should generate valid event activity interface")
  void shouldGenerateValidEventActivityInterface() throws Exception {
    Path activity = OUTPUT.resolve("cbs/dsl/codegen/generated/LoanDisbursementEventDsl_LoanDisbursementEventActivity.java");
    assertTrue(Files.exists(activity), "Activity interface should exist");
    String content = Files.readString(activity);
    assertTrue(content.contains(" @ActivityInterface"));
    parseJava(activity);
  }

  @Test @DisplayName("should generate valid event specification interface")
  void shouldGenerateValidEventSpecificationInterface() throws Exception {
    Path spec = OUTPUT.resolve("cbs/dsl/codegen/generated/LoanDisbursementEventSpecification.java");
    assertTrue(Files.exists(spec), "Specification interface should exist");
    String content = Files.readString(spec);
    assertTrue(content.contains(" @WorkflowInterface"));
    assertTrue(content.contains("interface LoanDisbursementEventSpecification"));
    parseJava(spec);
  }

  @Test @DisplayName("should generate valid event specification implementation")
  void shouldGenerateValidEventSpecificationImplementation() throws Exception {
    Path impl = OUTPUT.resolve("cbs/dsl/codegen/generated/LoanDisbursementEventSpecificationImpl.java");
    assertTrue(Files.exists(impl), "Specification implementation should exist");
    String content = Files.readString(impl);
    assertTrue(content.contains("implements LoanDisbursementEventSpecification"));
    parseJava(impl);
  }

  @Test @DisplayName("should generate valid event definition")
  void shouldGenerateValidEventDefinition() throws Exception {
    Path def = OUTPUT.resolve("cbs/dsl/codegen/generated/definitions/LoanDisbursementEventDsl_LoanDisbursementDefinition.java");
    assertTrue(Files.exists(def), "Definition file should exist");
    String content = Files.readString(def);
    assertTrue(content.contains("class LoanDisbursementEventDsl_LoanDisbursementDefinition"));
    parseJava(def);
  }

  private static void parseJava(Path file) throws Exception {
    ParserConfiguration config = new ParserConfiguration();
    config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25);
    JavaParser parser = new JavaParser(config);
    var result = parser.parse(file);
    if (!result.isSuccessful()) {
      throw new AssertionError("Parse errors in " + file + ": " + result.getProblems());
    }
  }
}
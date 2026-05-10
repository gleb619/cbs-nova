import static org.junit.jupiter.api.Assertions.*;

import cbs.dsl.codegen.DslCompiler;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class DebitFundingAccountTransactionDslTest {

  private static final Path OUTPUT = Path.of("build/dsl-classes");

  @Test
  @DisplayName("should parse DSL source as valid implicit class")
  void shouldParseDslSourceAsValidImplicitClass() throws Exception {
    Path source = Path.of("src/DebitFundingAccountTransactionDsl.java");
    String content = Files.readString(source);
    assertFalse(DslCompiler.containsExplicitTypeDeclaration(content));
    DslCompiler.ParsedDsl parsed = DslCompiler.parseCompactDsl(content);
    assertFalse(parsed.body().isEmpty());
    assertTrue(parsed.body().contains("DEBIT_FUNDING_ACCOUNT"));
  }

  @Test
  @DisplayName("should generate compiled class file")
  void shouldGenerateCompiledClassFile() {
    assertTrue(Files.exists(OUTPUT.resolve("DebitFundingAccountTransactionDsl.class")));
  }

  @Test
  @DisplayName("should generate valid transaction activity interface")
  void shouldGenerateValidTransactionActivityInterface() throws Exception {
    Path activity = OUTPUT.resolve(
        "cbs/dsl/codegen/generated/DebitFundingAccountTransactionDsl_DebitFundingAccountActivity.java");
    assertTrue(Files.exists(activity), "Activity interface should exist");
    String content = Files.readString(activity);
    assertTrue(content.contains("@ActivityInterface"));
    assertTrue(content.contains(
        "interface DebitFundingAccountTransactionDsl_DebitFundingAccountActivity"));
    parseJava(activity);
  }

  @Test
  @DisplayName("should generate valid transaction definition")
  void shouldGenerateValidTransactionDefinition() throws Exception {
    Path def = OUTPUT.resolve(
        "cbs/dsl/codegen/generated/definitions/DebitFundingAccountTransactionDsl_DebitFundingAccountDefinition.java");
    assertTrue(Files.exists(def), "Definition file should exist");
    String content = Files.readString(def);
    assertTrue(content.contains(
        "class DebitFundingAccountTransactionDsl_DebitFundingAccountDefinition"));
    assertTrue(content.contains("implements TransactionDefinition"));
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

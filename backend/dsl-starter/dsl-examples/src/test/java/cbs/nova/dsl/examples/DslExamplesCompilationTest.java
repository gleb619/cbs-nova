package cbs.nova.dsl.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T703 smoke gate for the dsl-examples showcase module.
 *
 * <p>
 * Modeled on {@code app/dsl}'s {@code DslCompilationTest}: the DSL compiler runs during
 * {@code compileJava}, so a green build already proves the examples compile. These assertions
 * additionally pin the generated artifacts per example, so deleting or renaming a generated
 * workflow/definition/service descriptor fails the gate instead of silently rotting the catalog.
 */
class DslExamplesCompilationTest {

  private static final Path GENERATED = Path.of("build", "generated");
  private static final Path EXAMPLES = Path.of("build", "generated", "cbs/nova/dslexamples/v1");
  private static final Path SOURCE_DSL = Path.of("src", "dsl");

  static Stream<String> exampleDslNames() throws IOException {
    try (var files = Files.list(SOURCE_DSL)) {
      return files
              .map(p -> p.getFileName().toString())
              .filter(name -> name.endsWith(".java"))
              .map(name -> name.substring(0, name.length() - ".java".length()))
              .sorted()
              .toList()
              .stream();
    }
  }

  @Test
  void everyExampleDslHasAGeneratedClass() throws IOException {
    var examples = exampleDslNames().toList();
    assertThat(examples).isNotEmpty();
    assertThat(examples).allSatisfy(name -> assertThat(EXAMPLES.resolve(name + ".java")).exists());
  }

  @ParameterizedTest
  @MethodSource("exampleDslNames")
  void everyExampleDslIsRegisteredInTheDefinitionProvider(String example) throws IOException {
    // The aggregate provider is the only ServiceLoader entry; it must reference every
    // example or the example silently disappears from the runtime registry.
    var provider = Files
            .readString(EXAMPLES.getParent().resolve("GeneratedDslDefinitionProvider.java"));
    assertThat(provider).contains("v1." + example + "(");
  }

  @Test
  void representativeProcessWorkflowIsGenerated() {
    assertThat(EXAMPLES.resolve("SimpleOrderProcessWorkflow.java")).exists();
    assertThat(EXAMPLES.resolve("SimpleOrderProcessDefinition.java")).exists();
  }

  @Test
  void representativeTransactionIsGenerated() {
    assertThat(EXAMPLES.resolve("chargePaymentTransactionActivity.java")).exists();
    assertThat(EXAMPLES.resolve("chargePaymentTransactionDefinition.java")).exists();
  }

  @Test
  void serviceLoaderDescriptorsAreGenerated() {
    assertThat(GENERATED.resolve("META-INF/services/cbs.nova.dsl.DslDefinitionProvider")).exists();
    assertThat(GENERATED.resolve("META-INF/services/cbs.nova.dsl.GeneratedClassProvider")).exists();
    assertThat(GENERATED.resolve("META-INF/services/cbs.nova.dsl.registry.ModelRegistry")).exists();
  }

  @Test
  void serviceLoaderDescriptorPointsAtTheAggregateProvider() throws IOException {
    List<String> providers = Files.readAllLines(
            GENERATED.resolve("META-INF/services/cbs.nova.dsl.DslDefinitionProvider")).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
    assertThat(providers).containsExactly("cbs.nova.dslexamples.GeneratedDslDefinitionProvider");
  }

  @Test
  void generatedModelClassesBackTheExamples() {
    assertThat(EXAMPLES.resolve("OrderSagaModels.java")).exists();
    assertThat(EXAMPLES.resolve("UnreliableApiModels.java")).exists();
  }
}

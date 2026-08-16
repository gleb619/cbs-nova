package cbs.nova.app.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DslCompilationTest {

  private static final Path GENERATED = Path.of("build", "generated");
  private static final Path PACKAGE = GENERATED.resolve("cbs/nova/app/dsl");

  @Test
  void generatedProcessInterfaceExists() throws IOException {
    assertThat(findFirst(PACKAGE.resolve("orderprocess"), "OrderProcessProcessWorkflow.java")).exists();
  }

  @Test
  void generatedProcessDefinitionExists() throws IOException {
    assertThat(findFirst(PACKAGE.resolve("orderprocess"), "OrderProcessProcessDefinition.java")).exists();
  }

  @Test
  void generatedTransactionInterfaceExists() throws IOException {
    assertThat(findFirst(PACKAGE.resolve("validateorder"), "VALIDATE_ORDERTransactionActivity.java")).exists();
  }

  @Test
  void generatedTransactionDefinitionExists() throws IOException {
    assertThat(findFirst(PACKAGE.resolve("validateorder"), "VALIDATE_ORDERTransactionDefinition.java")).exists();
  }

  @Test
  void generatedServiceLoaderDescriptorExists() {
    assertThat(GENERATED.resolve("META-INF/services/cbs.nova.dsl.DslDefinitionProvider")).exists();
  }

  private static Path findFirst(Path base, String fileName) throws IOException {
    try (var stream = Files.walk(base, 2)) {
      return stream
          .filter(p -> p.getFileName().toString().equals(fileName))
          .findFirst()
          .orElse(base.resolve(fileName));
    }
  }
}

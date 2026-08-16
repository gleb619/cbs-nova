package cbs.nova.app.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DslCompilationTest {

  private static final Path GENERATED = Path.of("build", "generated");
  private static final String PACKAGE = "cbs/nova/app/dsl";

  @Test
  void generatedProcessInterfaceExists() {
    assertThat(GENERATED.resolve(PACKAGE).resolve("orderprocess/v6a49453/OrderProcessProcessWorkflow.java")).exists();
  }

  @Test
  void generatedProcessDefinitionExists() {
    assertThat(GENERATED.resolve(PACKAGE).resolve("orderprocess/v6a49453/OrderProcessProcessDefinition.java")).exists();
  }

  @Test
  void generatedTransactionInterfaceExists() {
    assertThat(GENERATED.resolve(PACKAGE).resolve("validateorder/v6a49453/VALIDATE_ORDERTransactionActivity.java")).exists();
  }

  @Test
  void generatedTransactionDefinitionExists() {
    assertThat(GENERATED.resolve(PACKAGE).resolve("validateorder/v6a49453/VALIDATE_ORDERTransactionDefinition.java")).exists();
  }

  @Test
  void generatedServiceLoaderDescriptorExists() {
    assertThat(GENERATED.resolve("META-INF/services/cbs.nova.dsl.DslDefinitionProvider")).exists();
  }
}

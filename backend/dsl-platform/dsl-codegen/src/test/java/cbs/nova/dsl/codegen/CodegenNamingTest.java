package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.model.CodegenNaming;
import org.junit.jupiter.api.Test;

class CodegenNamingTest {

  private final CodegenNaming naming = new CodegenNaming();

  @Test
  void fallsBackToBasePackageWhenTargetPackageIsNull() {
    assertThat(naming.versionedPackage("Loan", "1"))
            .isEqualTo("cbs.nova.dsl.generated.loan.v1");
  }

  @Test
  void fallsBackToBasePackageWhenTargetPackageIsBlank() {
    assertThat(naming.versionedPackage("Loan", "1", ""))
            .isEqualTo("cbs.nova.dsl.generated.loan.v1");
    assertThat(naming.versionedPackage("Loan", "1", "   "))
            .isEqualTo("cbs.nova.dsl.generated.loan.v1");
  }

  @Test
  void usesTargetPackageWhenProvided() {
    assertThat(naming.versionedPackage("Loan", "1", "com.example.workflow"))
            .isEqualTo("com.example.workflow.loan.v1");
  }
}

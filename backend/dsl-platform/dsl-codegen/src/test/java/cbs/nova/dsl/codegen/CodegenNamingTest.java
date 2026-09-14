package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.model.CodegenNaming;
import org.junit.jupiter.api.Test;

class CodegenNamingTest {

  private static final String CUSTOM_BASE = "com.example.workflow";
  private static final String CONSTANT_BASE = CompileConfig.DEFAULT_GENERATED_BASE_PACKAGE;

  private final CodegenNaming defaultNaming = new CodegenNaming(CONSTANT_BASE);
  private final CodegenNaming customNaming = new CodegenNaming(CUSTOM_BASE);

  @Test
  void registryPackageFallsBackToConstantWhenTargetPackageIsNull() {
    assertThat(defaultNaming.registryPackage(null))
            .isEqualTo(CONSTANT_BASE);
  }

  @Test
  void registryPackageFallsBackToCustomBasePackageWhenTargetPackageIsBlank() {
    assertThat(customNaming.registryPackage(""))
            .isEqualTo(CUSTOM_BASE);
    assertThat(customNaming.registryPackage("   "))
            .isEqualTo(CUSTOM_BASE);
  }

  @Test
  void registryPackageUsesExplicitTargetPackageWhenProvided() {
    assertThat(customNaming.registryPackage("org.acme.override"))
            .isEqualTo("org.acme.override");
  }

  @Test
  void versionedPackagePrefersExplicitArgOverCustomBaseOverConstant() {
    assertThat(defaultNaming.versionedPackage("Loan", "1", null))
            .isEqualTo("cbs.nova.dsl.generated.v1.loan");
    assertThat(customNaming.versionedPackage("Loan", "1", null))
            .isEqualTo("com.example.workflow.v1.loan");
    assertThat(customNaming.versionedPackage("Loan", "1", "org.acme.override"))
            .isEqualTo("org.acme.override.v1.loan");
  }

  @Test
  void versionedBasePackagePrefersExplicitArgOverCustomBaseOverConstant() {
    assertThat(defaultNaming.versionedBasePackage("1", null))
            .isEqualTo("cbs.nova.dsl.generated.v1");
    assertThat(customNaming.versionedBasePackage("1", null))
            .isEqualTo("com.example.workflow.v1");
    assertThat(customNaming.versionedBasePackage("1", "org.acme.override"))
            .isEqualTo("org.acme.override.v1");
  }

  @Test
  void fallsBackToBasePackageWhenTargetPackageIsBlank() {
    assertThat(defaultNaming.versionedPackage("Loan", "1", ""))
            .isEqualTo("cbs.nova.dsl.generated.v1.loan");
    assertThat(defaultNaming.versionedPackage("Loan", "1", "   "))
            .isEqualTo("cbs.nova.dsl.generated.v1.loan");
  }

  @Test
  void usesTargetPackageWhenProvided() {
    assertThat(defaultNaming.versionedPackage("Loan", "1", "com.example.workflow"))
            .isEqualTo("com.example.workflow.v1.loan");
  }
}

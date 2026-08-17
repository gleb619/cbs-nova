package cbs.nova.dsl.codegen.util;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.codegen.model.CodegenNaming;
import org.junit.jupiter.api.Test;

class DslPackageNameResolverTest {

  private final DslPackageNameResolver resolver = new DslPackageNameResolver(new CodegenNaming());

  @Test
  void usesFileNameSubPackageByDefault() {
    assertThat(resolver.resolve("cbs.nova.dslexamples", "v1", "BatchProcessingDsl.java", true))
            .isEqualTo("cbs.nova.dslexamples.batchprocessing.v1");
  }

  @Test
  void stripsJavaExtensionAndDslSuffix() {
    assertThat(resolver.resolve("cbs.nova.dslexamples", "v2", "MyWorkflowDsl.java", true))
            .isEqualTo("cbs.nova.dslexamples.myworkflow.v2");
  }

  @Test
  void sanitizesFileNameToLowercaseAlphanumeric() {
    assertThat(resolver.resolve("cbs.nova.dslexamples", "v1", "Some-DSL_v2-File.java", true))
            .isEqualTo("cbs.nova.dslexamples.somedslv2file.v1");
  }

  @Test
  void skipsFileNameSegmentWhenFlagDisabled() {
    assertThat(resolver.resolve("cbs.nova.dslexamples", "v1", "BatchProcessingDsl.java", false))
            .isEqualTo("cbs.nova.dslexamples.v1");
  }

  @Test
  void prependsVToDigitOnlyVersion() {
    assertThat(resolver.resolve("cbs.nova.dslexamples", "42", "BatchProcessing.java", true))
            .isEqualTo("cbs.nova.dslexamples.batchprocessing.v42");
    assertThat(resolver.resolve("cbs.nova.dslexamples", "42", "BatchProcessing.java", false))
            .isEqualTo("cbs.nova.dslexamples.v42");
  }

  @Test
  void fallsBackToV1WhenVersionIsNullOrBlank() {
    assertThat(resolver.resolve("cbs.nova.dslexamples", null, "BatchProcessing.java", true))
            .isEqualTo("cbs.nova.dslexamples.batchprocessing.v1");
    assertThat(resolver.resolve("cbs.nova.dslexamples", "  ", "BatchProcessing.java", false))
            .isEqualTo("cbs.nova.dslexamples.v1");
  }

  @Test
  void fallsBackToDefaultBasePackageWhenBasePackageIsNullOrBlank() {
    assertThat(resolver.resolve(null, "v1", "BatchProcessing.java", true))
            .isEqualTo("cbs.nova.dsl.generated.batchprocessing.v1");
    assertThat(resolver.resolve("  ", "v1", "BatchProcessing.java", false))
            .isEqualTo("cbs.nova.dsl.generated.v1");
  }

  @Test
  void handlesFileNameWithoutDslSuffix() {
    assertThat(resolver.resolve("cbs.nova.dslexamples", "v1", "BatchProcessing.java", true))
            .isEqualTo("cbs.nova.dslexamples.batchprocessing.v1");
  }
}

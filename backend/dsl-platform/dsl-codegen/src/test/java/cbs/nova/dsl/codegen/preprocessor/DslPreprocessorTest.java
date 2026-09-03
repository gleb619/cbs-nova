package cbs.nova.dsl.codegen.preprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.codegen.preprocessor.DslPreprocessor.Result;
import org.junit.jupiter.api.Test;

class DslPreprocessorTest {

  private DslPreprocessor dslPreprocessor = new DslPreprocessor();

  private static final String VALID_COMPACT_SOURCE = """
          List<DslObject> define() {
            return List.of();
          }
          """;

  @Test
  void classNameIsFileNameMinusJavaExtension() {
    var result = dslPreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE, null);

    assertThat(result.className()).isEqualTo("OrderSaga");
  }

  @Test
  void fileNameWithoutJavaExtensionThrows() {
    assertThatThrownBy(
            () -> dslPreprocessor.preprocess("OrderSaga", VALID_COMPACT_SOURCE, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must end with .java");
  }

  @Test
  void twoArgOverloadEmitsNoPackageLine() {
    var result = dslPreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE, null);

    assertThat(result.preprocessedSource())
            .doesNotContainPattern("(?m)^package\\s+\\w+;");
  }

  @Test
  void threeArgOverloadEmitsTargetPackageLine() {
    var result = dslPreprocessor.preprocess(
            "OrderSaga.java", VALID_COMPACT_SOURCE, "com.example.generated");

    assertThat(result.preprocessedSource())
            .startsWith("package com.example.generated;");
  }

  @Test
  void importsAreHoistedInOriginalRelativeOrder() {
    var source = """
            import java.util.List;

            List<DslObject> define() {
              return new java.util.ArrayList<>();
            }
            """;

    var result = dslPreprocessor.preprocess("OrderSaga.java", source, null);

    var output = result.preprocessedSource();
    assertThat(output).contains("import java.util.List;");
    assertThat(output).doesNotContain("import java.util.ArrayList;");
    assertThat(output.indexOf("import java.util.List;"))
            .isLessThan(output.indexOf("public class OrderSaga"));
  }

  @Test
  void duplicateImportLinesAreNotDeduplicated() {
    var source = """
            import java.util.List;
            import java.util.List;

            List<DslObject> define() {
              return List.of();
            }
            """;

    var result = dslPreprocessor.preprocess("OrderSaga.java", source, null);

    assertThat(result.preprocessedSource())
            .contains("import java.util.List;\nimport java.util.List;");
  }

  @Test
  void generatedSourceImplementsDslCompactSource() {
    var result = dslPreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE, null);

    assertThat(result.preprocessedSource())
            .contains("public class OrderSaga")
            .contains("implements DslCompactSource");
  }

  @Test
  void defineMethodModifierIsNormalizedToPublicOverride() {
    var source = """
            List<DslObject> define() {
              return List.of();
            }
            """;

    var result = dslPreprocessor.preprocess("OrderSaga.java", source, null);

    var output = result.preprocessedSource();
    assertThat(output).contains("public @Override List<DslObject> define() {");
    assertThat(output).contains("  return List.of();");
    assertThat(output).contains("}");
  }

  @Test
  void sourceWithoutDefineMethodIsRejected() {
    var source = """
            // no define() method here
            int unrelated = 42;
            """;

    assertThatThrownBy(() -> dslPreprocessor.preprocess("OrderSaga.java", source, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is not a valid compact DSL source")
            .hasMessageContaining("must declare a List<DslObject> define() method");
  }

  @Test
  void sourceWithTopLevelTypeIsRejected() {
    var source = """
            public class Foo {
            }
            """;

    assertThatThrownBy(() -> dslPreprocessor.preprocess("OrderSaga.java", source, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not declare a top-level class/interface/enum/record");
  }

  @Test
  void sourceWithPackageDeclarationIsRejected() {
    var source = """
            package com.foo;

            List<DslObject> define() {
              return List.of();
            }
            """;

    assertThatThrownBy(() -> dslPreprocessor.preprocess("OrderSaga.java", source, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not declare a package");
  }

  @Test
  void multipleViolationsAreCommaJoinedInMessage() {
    var source = """
            package com.foo;

            int unrelated = 42;
            """;

    assertThatThrownBy(() -> dslPreprocessor.preprocess("OrderSaga.java", source, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not declare a package")
            .hasMessageContaining("must declare a List<DslObject> define() method")
            .hasMessageContaining(
                    "must not declare a package, must declare a List<DslObject> define() method");
  }

  @Test
  void resultRecordExposesNonBlankClassNameAndSource() {
    var result = dslPreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE, null);

    assertThat(result).isInstanceOf(Result.class);
    assertThat(result.className()).isNotNull().isNotBlank().isEqualTo("OrderSaga");
    assertThat(result.preprocessedSource())
            .isNotNull()
            .isNotBlank()
            .contains("public class OrderSaga")
            .contains("implements DslCompactSource")
            .contains("public @Override List<DslObject> define() {");
  }

  @Test
  void isValidCompactSourceMatchesPreprocessAcceptance() {
    assertThat(dslPreprocessor.isValidCompactSource(VALID_COMPACT_SOURCE)).isTrue();
    assertThat(dslPreprocessor.isValidCompactSource("package com.foo;")).isFalse();
    assertThat(dslPreprocessor.isValidCompactSource("public class X {}")).isFalse();
    assertThat(dslPreprocessor.isValidCompactSource("// no define here")).isFalse();
  }

  @Test
  void defaultImportsAreInjectedForCompactSources() {
    var source = """
            List<DslObject> define() {
              return Dsl.process("P").execute(ctx -> Result.success(Map.of())).buildList();
            }
            """;

    var result = dslPreprocessor.preprocess("Sample.java", source, null);
    var output = result.preprocessedSource();

    assertThat(output).contains("import cbs.nova.dsl.*;");
    assertThat(output).contains("import java.time.*;");
    assertThat(output).contains("import java.util.*;");
    assertThat(output).contains("import java.util.stream.*;");
  }

  @Test
  void defaultImportsAreSkippedWhenAlreadyPresent() {
    var source = """
            import cbs.nova.dsl.*;
            import java.util.List;

            List<DslObject> define() {
              return List.of();
            }
            """;

    var result = dslPreprocessor.preprocess("Sample.java", source, null);
    var output = result.preprocessedSource();

    long count = output.lines().filter(l -> l.equals("import cbs.nova.dsl.*;")).count();
    assertThat(count).isEqualTo(1);
  }
}

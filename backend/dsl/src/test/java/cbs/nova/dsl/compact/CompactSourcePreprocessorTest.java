package cbs.nova.dsl.compact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.compact.CompactSourcePreprocessor.Result;
import org.junit.jupiter.api.Test;

class CompactSourcePreprocessorTest {

  private static final String VALID_COMPACT_SOURCE = """
          List<DslObject> define() {
            return List.of();
          }
          """;

  // ---------------------------------------------------------------------
  // 1. className derivation
  // ---------------------------------------------------------------------

  @Test
  void classNameIsFileNameMinusJavaExtension() {
    var result = CompactSourcePreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE);

    assertThat(result.className()).isEqualTo("OrderSaga");
  }

  @Test
  void fileNameWithoutJavaExtensionThrows() {
    // The className() check only fires if isValidCompactSource already passed,
    // so we use an otherwise-valid compact source whose fileName lacks .java.
    assertThatThrownBy(
            () -> CompactSourcePreprocessor.preprocess("OrderSaga", VALID_COMPACT_SOURCE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must end with .java");
  }

  // ---------------------------------------------------------------------
  // 2. Default-package overload (2-arg preprocess) — no package line emitted
  // ---------------------------------------------------------------------

  @Test
  void twoArgOverloadEmitsNoPackageLine() {
    var result = CompactSourcePreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE);

    // The wrapInClass logic only emits a `package X;` line when targetPackage
    // is non-null and non-blank. The 2-arg overload passes null, so the
    // generated source must contain no `package X;` declaration at all.
    assertThat(result.preprocessedSource())
            .doesNotContainPattern("(?m)^package\\s+\\w+;");
  }

  // ---------------------------------------------------------------------
  // 3. Explicit-package overload (3-arg preprocess) — package line emitted verbatim
  // ---------------------------------------------------------------------

  @Test
  void threeArgOverloadEmitsTargetPackageLine() {
    var result = CompactSourcePreprocessor.preprocess(
            "OrderSaga.java", VALID_COMPACT_SOURCE, "com.example.generated");

    assertThat(result.preprocessedSource())
            .startsWith("package com.example.generated;");
  }

  // ---------------------------------------------------------------------
  // 4. Import hoisting — imports are extracted (no dedup) and preserve order
  // ---------------------------------------------------------------------

  @Test
  void importsAreHoistedInOriginalRelativeOrder() {
    var source = """
            import java.util.List;

            List<DslObject> define() {
              return new java.util.ArrayList<>();
            }
            """;

    var result = CompactSourcePreprocessor.preprocess("OrderSaga.java", source);

    var output = result.preprocessedSource();
    assertThat(output).contains("import java.util.List;");
    // `java.util.ArrayList` is referenced as a fully-qualified type in the body,
    // not as an import, so it must NOT be hoisted as an import line. We assert
    // that no `import java.util.ArrayList;` line was synthesized.
    assertThat(output).doesNotContain("import java.util.ArrayList;");
    // The import line must appear before the class declaration.
    assertThat(output.indexOf("import java.util.List;"))
            .isLessThan(output.indexOf("public class OrderSaga"));
  }

  @Test
  void duplicateImportLinesAreNotDeduplicated() {
    // splitImports performs a single linear scan and concatenates each matched
    // import verbatim — there is no deduplication step. The same import line
    // appearing twice in the input must therefore appear twice in the output.
    var source = """
            import java.util.List;
            import java.util.List;

            List<DslObject> define() {
              return List.of();
            }
            """;

    var result = CompactSourcePreprocessor.preprocess("OrderSaga.java", source);

    assertThat(result.preprocessedSource())
            .contains("import java.util.List;\nimport java.util.List;");
  }

  // ---------------------------------------------------------------------
  // 5. Generated source implements DslCompactSource
  // ---------------------------------------------------------------------

  @Test
  void generatedSourceImplementsDslCompactSource() {
    var result = CompactSourcePreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE);

    assertThat(result.preprocessedSource())
            .contains("public class OrderSaga")
            .contains("implements cbs.nova.dsl.DslCompactSource");
  }

  // ---------------------------------------------------------------------
  // 6. define() normalization — bare modifier becomes `public @Override`
  // ---------------------------------------------------------------------

  @Test
  void defineMethodModifierIsNormalizedToPublicOverride() {
    // Input has a bare (no `public`) define() method. The preprocessor
    // replaces the matched signature line with `public @Override ...`,
    // regardless of the original modifier.
    var source = """
            List<DslObject> define() {
              return List.of();
            }
            """;

    var result = CompactSourcePreprocessor.preprocess("OrderSaga.java", source);

    var output = result.preprocessedSource();
    assertThat(output).contains("public @Override List<DslObject> define() {");
    // The rest of the original method body must be preserved verbatim after
    // the normalized signature line.
    assertThat(output).contains("  return List.of();");
    assertThat(output).contains("}");
  }

  // ---------------------------------------------------------------------
  // 7. Missing define() method
  // ---------------------------------------------------------------------

  @Test
  void sourceWithoutDefineMethodIsRejected() {
    var source = """
            // no define() method here
            int unrelated = 42;
            """;

    assertThatThrownBy(() -> CompactSourcePreprocessor.preprocess("OrderSaga.java", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is not a valid compact DSL source")
            .hasMessageContaining("must declare a List<DslObject> define() method");
  }

  // ---------------------------------------------------------------------
  // 8. Top-level type declaration is forbidden
  // ---------------------------------------------------------------------

  @Test
  void sourceWithTopLevelTypeIsRejected() {
    var source = """
            public class Foo {
            }
            """;

    assertThatThrownBy(() -> CompactSourcePreprocessor.preprocess("OrderSaga.java", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not declare a top-level class/interface/enum/record");
  }

  // ---------------------------------------------------------------------
  // 9. Package declaration is forbidden
  // ---------------------------------------------------------------------

  @Test
  void sourceWithPackageDeclarationIsRejected() {
    var source = """
            package com.foo;

            List<DslObject> define() {
              return List.of();
            }
            """;

    assertThatThrownBy(() -> CompactSourcePreprocessor.preprocess("OrderSaga.java", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not declare a package");
  }

  // ---------------------------------------------------------------------
  // 10. Multiple simultaneous violations — comma-joined message
  // ---------------------------------------------------------------------

  @Test
  void multipleViolationsAreCommaJoinedInMessage() {
    // Source has BOTH a package declaration AND no define() method.
    // validationErrors checks all three conditions independently (no
    // short-circuiting), so both applicable fragments must appear in the
    // thrown message, joined by ", ".
    var source = """
            package com.foo;

            int unrelated = 42;
            """;

    assertThatThrownBy(() -> CompactSourcePreprocessor.preprocess("OrderSaga.java", source))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not declare a package")
            .hasMessageContaining("must declare a List<DslObject> define() method")
            // Both fragments are appended in the documented order: package, then
            // top-level, then define-absence. With a package present and no
            // top-level type, the joined message is exactly:
            // "must not declare a package, must declare a List<DslObject> define() method"
            .hasMessageContaining(
                    "must not declare a package, must declare a List<DslObject> define() method");
  }

  // ---------------------------------------------------------------------
  // 11. Result record shape — both fields populated for a valid input
  // ---------------------------------------------------------------------

  @Test
  void resultRecordExposesNonBlankClassNameAndSource() {
    var result = CompactSourcePreprocessor.preprocess("OrderSaga.java", VALID_COMPACT_SOURCE);

    assertThat(result).isInstanceOf(Result.class);
    assertThat(result.className()).isNotNull().isNotBlank().isEqualTo("OrderSaga");
    assertThat(result.preprocessedSource())
            .isNotNull()
            .isNotBlank()
            .contains("public class OrderSaga")
            .contains("implements cbs.nova.dsl.DslCompactSource")
            .contains("public @Override List<DslObject> define() {");
  }

  // ---------------------------------------------------------------------
  // Sanity: a round-trip isValidCompactSource agrees with preprocess()
  // ---------------------------------------------------------------------

  @Test
  void isValidCompactSourceMatchesPreprocessAcceptance() {
    assertThat(CompactSourcePreprocessor.isValidCompactSource(VALID_COMPACT_SOURCE)).isTrue();
    assertThat(CompactSourcePreprocessor.isValidCompactSource("package com.foo;")).isFalse();
    assertThat(CompactSourcePreprocessor.isValidCompactSource("public class X {}")).isFalse();
    assertThat(CompactSourcePreprocessor.isValidCompactSource("// no define here")).isFalse();
  }
}

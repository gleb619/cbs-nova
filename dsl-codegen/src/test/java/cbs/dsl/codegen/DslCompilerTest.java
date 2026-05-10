package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DslCompilerTest {

  @Test
  @DisplayName("Should detect explicit class declaration")
  void shouldDetectExplicitClassDeclaration() {
    String explicitClass = """
        package com.example;

        public class MyClass {
            public static void main(String[] args) {}
        }
        """;
    assertTrue(DslCompiler.containsExplicitTypeDeclaration(explicitClass));
  }

  @Test
  @DisplayName("Should detect implicit class file")
  void shouldDetectImplicitClassFile() {
    String implicitClass = """
        import cbs.dsl.builder.EventDsl;

        EventDsl.event("TEST").build();
        """;
    assertFalse(DslCompiler.containsExplicitTypeDeclaration(implicitClass));
  }

  @Test
  @DisplayName("Should parse compact DSL with define method")
  void shouldParseCompactDslWithDefineMethod() {
    String compactDsl = """
        import cbs.dsl.builder.Dsl;
        import java.util.Map;

        List<DslObject> define() {
            return Dsl.helpers()
                .helper("TEST", h -> h
                    .parameters(reg -> reg.string("name"))
                    .execute(ctx -> ctx))
                .build();
        }
        """;

    DslCompiler.ParsedDsl parsed = DslCompiler.parseCompactDsl(compactDsl);

    assertTrue(parsed.imports().contains("import cbs.dsl.builder.Dsl;"));
    assertTrue(parsed.imports().contains("import java.util.Map;"));
    assertTrue(parsed.body().contains("return"));
    assertTrue(parsed.body().contains(".build();"));
    assertFalse(parsed.body().contains("define"));
  }

  @Test
  @DisplayName("Should strip comments from compact DSL")
  void shouldStripCommentsFromCompactDsl() {
    String dslWithComments = """
        import cbs.dsl.builder.Dsl;

        // line comment
        /* block comment */
        List<DslObject> define() {
            return List.of(Dsl.event("TEST")
                .build());
        }
        """;

    DslCompiler.ParsedDsl parsed = DslCompiler.parseCompactDsl(dslWithComments);

    assertTrue(parsed.imports().contains("import cbs.dsl.builder.Dsl;"));
    assertFalse(parsed.body().contains("// line comment"));
    assertFalse(parsed.body().contains("/* block comment */"));
    assertTrue(parsed.body().contains("return"));
  }

  @Test
  @DisplayName("Should throw when no define method found")
  void shouldThrowWhenNoDefineMethodFound() {
    String noDefine = """
        import cbs.dsl.builder.Dsl;

        Dsl.event("TEST").build();
        """;

    assertThrows(IllegalStateException.class, () -> DslCompiler.parseCompactDsl(noDefine));
  }

  @Test
  @DisplayName("Should parse compact DSL with List.of wrapper")
  void shouldParseCompactDslWithListOfWrapper() {
    String compactDsl = """
        import cbs.dsl.api.DslObject;
        import cbs.dsl.builder.Dsl;
        import java.util.List;

        List<DslObject> define() {
            return List.of(Dsl.event("MY_EVENT")
                .parameters(reg -> reg.string("name"))
                .build());
        }
        """;

    DslCompiler.ParsedDsl parsed = DslCompiler.parseCompactDsl(compactDsl);

    assertTrue(parsed.body().contains("List.of(Dsl.event(\"MY_EVENT\")"));
    assertTrue(parsed.body().contains(".build());"));
  }
}

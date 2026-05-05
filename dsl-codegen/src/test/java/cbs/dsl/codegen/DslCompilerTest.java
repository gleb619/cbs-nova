package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  @DisplayName("Should strip comments and extract DSL body")
  void shouldStripCommentsAndExtractDslBody() {
    String dslWithComments = """
        import cbs.dsl.builder.EventDsl;
        import java.util.Map;

        // line comment
        /* block comment */
        /**
         * long comment
         */
        EventDsl.event("SAMPLE_EVENT_DSL")
            .requiredParam("name")
            .context(ctx -> {
              Object helperResult = ctx.helper("SAMPLE_HELPER", Map.of("someVal", ctx.get("name")));
              ctx.put("enriched", helperResult);
            })
            .transaction("SAMPLE_TX")
            .transaction("SAMPLE_TRANSACTION_DSL")
            .finish((ctx, ex) -> {})
            .build();
        """;

    DslCompiler.ParsedDsl parsed = DslCompiler.parseImplicitClassWithJavaParser(dslWithComments);

    assertTrue(parsed.imports().contains("import cbs.dsl.builder.EventDsl;"));
    assertTrue(parsed.imports().contains("import java.util.Map;"));

    String body = parsed.body();
    assertFalse(body.contains("// line comment"));
    assertFalse(body.contains("/* block comment */"));
    assertFalse(body.contains("/**"));
    assertFalse(body.contains(" * long comment"));

    assertTrue(body.contains("EventDsl.event(\"SAMPLE_EVENT_DSL\")"));
    assertTrue(body.contains(".requiredParam(\"name\")"));
    assertTrue(body.contains(".transaction(\"SAMPLE_TX\")"));
    assertTrue(body.contains(".transaction(\"SAMPLE_TRANSACTION_DSL\")"));
    assertTrue(body.contains(".build();"));
  }

  @Test
  @DisplayName("Should handle implicit class file without imports")
  void shouldHandleImplicitClassFileWithoutImports() {
    String implicitClass = """
        // just a comment
        EventDsl.event("TEST").build();
        """;

    DslCompiler.ParsedDsl parsed = DslCompiler.parseImplicitClassWithJavaParser(implicitClass);

    assertEquals("", parsed.imports());
    assertTrue(parsed.body().contains("EventDsl.event(\"TEST\").build();"));
    assertFalse(parsed.body().contains("// just a comment"));
  }
}

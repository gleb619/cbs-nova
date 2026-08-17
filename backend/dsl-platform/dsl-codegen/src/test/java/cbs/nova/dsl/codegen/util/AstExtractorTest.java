package cbs.nova.dsl.codegen.util;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslObject.DslType;
import org.junit.jupiter.api.Test;

import java.util.List;

class AstExtractorTest {

  private final Json json = new Json();
  private final AstExtractor extractor = new AstExtractor(json);

  @Test
  void happyPathExtractsLambdaAstAsJson() {
    String source = """
            class Fixture {
              void run() {
                process("p").execute(ctx -> ctx.sleep("a"));
              }
            }
            """;

    String result = extractor.extract(List.of(source), "p", DslType.PROCESS);

    assertThat(result).isNotEqualTo("{}");
    assertThat(result).contains("\"type\":\"LambdaExpr\"");
  }

  @Test
  void transactionTypeUsesTransactionBuilder() {
    String source = """
            class Fixture {
              void run() {
                transaction("t").execute(ctx -> {});
              }
            }
            """;

    String result = extractor.extract(List.of(source), "t", DslType.TRANSACTION);

    assertThat(result).contains("\"type\":\"LambdaExpr\"");
  }

  @Test
  void nameMismatchReturnsEmptyObject() {
    String source = """
            class Fixture {
              void run() {
                process("p").execute(ctx -> {});
              }
            }
            """;

    String result = extractor.extract(List.of(source), "q", DslType.PROCESS);

    assertThat(result).isEqualTo("{}");
  }

  @Test
  void builderWithoutExecuteReturnsEmptyObject() {
    String source = """
            class Fixture {
              void run() {
                process("p").build(ctx -> {});
              }
            }
            """;

    String result = extractor.extract(List.of(source), "p", DslType.PROCESS);

    assertThat(result).isEqualTo("{}");
  }

  @Test
  void allUnparseableSourcesReturnEmptyObject() {
    String result = extractor.extract(List.of("{{{ not valid java"), "p", DslType.PROCESS);

    assertThat(result).isEqualTo("{}");
  }

  @Test
  void outputIsDeterministic() {
    String source = """
            class Fixture {
              void run() {
                process("p").execute(ctx -> ctx.sleep("a"));
              }
            }
            """;

    String first = extractor.extract(List.of(source), "p", DslType.PROCESS);
    String second = extractor.extract(List.of(source), "p", DslType.PROCESS);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void toTreeMapsLiteralsAndExpressions() {
    String source = """
            class Fixture {
              String field;
              String s;
              void run() {
                process("p").execute(ctx -> {
                  helper("x", 1, 1L, 1.0, true, null, 'c', s, this.field);
                });
              }
              void helper(String a, int b, long c, double d, boolean e, Object f, char g, String h, String i) {}
            }
            """;

    String result = extractor.extract(List.of(source), "p", DslType.PROCESS);

    assertThat(result)
            .contains("\"type\":\"LambdaExpr\"")
            .contains("\"type\":\"StringLiteralExpr\"")
            .contains("\"type\":\"IntegerLiteralExpr\"")
            .contains("\"type\":\"LongLiteralExpr\"")
            .contains("\"type\":\"DoubleLiteralExpr\"")
            .contains("\"type\":\"BooleanLiteralExpr\"")
            .contains("\"type\":\"NullLiteralExpr\"")
            .contains("\"type\":\"CharLiteralExpr\"")
            .contains("\"type\":\"SimpleName\"")
            .contains("\"type\":\"NameExpr\"")
            .contains("\"type\":\"MethodCallExpr\"")
            .contains("\"type\":\"FieldAccessExpr\"")
            .contains("\"children\":[")
            .contains("\"value\":\"x\"")
            .contains("\"value\":null")
            .contains("\"value\":\"helper\"")
            .contains("\"name\":\"helper\"")
            .contains("\"name\":\"field\"");
  }

  @Test
  void rangeFieldFormattedAsLineColumnLineColumn() {
    String source = """
            class Fixture {
              void run() {
                process("p").execute(ctx -> {});
              }
            }
            """;

    String result = extractor.extract(List.of(source), "p", DslType.PROCESS);

    assertThat(result).containsPattern("\"range\":\"\\d+:\\d+-\\d+:\\d+\"");
  }
}

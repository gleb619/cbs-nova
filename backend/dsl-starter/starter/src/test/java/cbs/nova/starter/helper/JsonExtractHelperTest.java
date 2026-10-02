package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.JsonExtractIn;
import cbs.nova.starter.helper.model.JsonExtractOut;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JsonExtractHelperTest {

  private final JsonExtractHelper helper = new JsonExtractHelper(new ObjectMapper());

  @Test
  void extractsTopLevelField() {
    var ctx = SimpleContext.<JsonExtractIn>builder().body(new JsonExtractIn("{\"a\":\"x\"}", "a"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("x");
  }

  @Test
  void extractsNestedDottedPath() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn("{\"a\":{\"b\":{\"c\":1}}}", "a.b.c"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("1");
  }

  @Test
  void extractsArrayElementByIndex() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn("{\"items\":[{\"id\":7},{\"id\":9}]}", "items.0.id"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("7");
  }

  @Test
  void returnsNotPresentForMissingPath() {
    var ctx = SimpleContext.<JsonExtractIn>builder().body(new JsonExtractIn("{\"a\":\"x\"}", "b"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void returnsFailureForMalformedJson() {
    var ctx = SimpleContext.<JsonExtractIn>builder().body(new JsonExtractIn("not json", "a"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("Invalid JSON");
  }

  @Test
  void returnsNotPresentForNullJson() {
    var ctx = SimpleContext.<JsonExtractIn>builder().body(new JsonExtractIn(null, "a"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void returnsNotPresentForEmptyJson() {
    var ctx = SimpleContext.<JsonExtractIn>builder().body(new JsonExtractIn("", "a"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void extractsJsonPathWithLeadingDollar() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn("{\"data\":{\"orderId\":\"abc-42\"}}", "$.data.orderId"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("abc-42");
  }

  @Test
  void dollarOnlyReturnsNotPresent() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn("{\"a\":1}", "$"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void extractsArrayElementWithBracketNotation() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn(
                    "{\"items\":[{\"id\":7},{\"id\":9}]}",
                    "$.items[0].id"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("7");
  }

  @Test
  void extractsSecondArrayElementViaBracketNotation() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn(
                    "{\"items\":[{\"id\":7},{\"id\":9}]}",
                    "items[1].id"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("9");
  }

  @Test
  void returnsNotPresentForOutOfRangeIndex() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn(
                    "{\"items\":[{\"id\":7},{\"id\":9}]}",
                    "$.items[5].id"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void returnsNotPresentForNonNumericIndex() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn(
                    "{\"items\":[{\"id\":7},{\"id\":9}]}",
                    "items[x].id"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void returnsNotPresentForUnclosedBracket() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn(
                    "{\"items\":[{\"id\":7}]}",
                    "items["))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void keysStartingWithDollarStayLiteralWhenNotJsonPathRoot() {
    // Backward compatibility: `$ref` / `$schema` were literal keys before `$` root support.
    for (String key : new String[]{"$ref", "$schema", "$id"}) {
      var ctx = SimpleContext.<JsonExtractIn>builder()
              .body(new JsonExtractIn("{\"" + key + "\":\"v\"}", key))
              .mode(ExecutionMode.PREVIEW).build();
      Result<JsonExtractOut> result = helper.execute(ctx);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value().present()).as(key).isTrue();
      assertThat(result.value().value()).as(key).isEqualTo("v");
    }
  }

  @Test
  void dollarRootStillWorksBeforeBracketIndex() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn("[{\"id\":3}]", "$[0].id"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("3");
  }

  @Test
  void keyLiterallyNamedMalformedSentinelIsExtractable() {
    var ctx = SimpleContext.<JsonExtractIn>builder()
            .body(new JsonExtractIn("{\"__malformed__\":\"ok\"}", "__malformed__"))
            .mode(ExecutionMode.PREVIEW).build();
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("ok");
  }
}

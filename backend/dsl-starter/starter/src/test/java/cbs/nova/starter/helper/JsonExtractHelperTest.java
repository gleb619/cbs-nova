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
}

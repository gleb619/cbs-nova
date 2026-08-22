package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.model.JsonExtractIn;
import cbs.nova.starter.helpers.model.JsonExtractOut;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JsonExtractHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final JsonExtractHelper helper = new JsonExtractHelper(new ObjectMapper());

  @Test
  void extractsTopLevelField() {
    var ctx = contextFactory.of(new JsonExtractIn("{\"a\":\"x\"}", "a"),
            ExecutionMode.PREVIEW);
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("x");
  }

  @Test
  void extractsNestedDottedPath() {
    var ctx = contextFactory.of(new JsonExtractIn("{\"a\":{\"b\":{\"c\":1}}}", "a.b.c"),
            ExecutionMode.PREVIEW);
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("1");
  }

  @Test
  void extractsArrayElementByIndex() {
    var ctx = contextFactory.of(
            new JsonExtractIn("{\"items\":[{\"id\":7},{\"id\":9}]}", "items.0.id"),
            ExecutionMode.PREVIEW);
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isTrue();
    assertThat(result.value().value()).isEqualTo("7");
  }

  @Test
  void returnsNotPresentForMissingPath() {
    var ctx = contextFactory.of(new JsonExtractIn("{\"a\":\"x\"}", "b"),
            ExecutionMode.PREVIEW);
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void returnsFailureForMalformedJson() {
    var ctx = contextFactory.of(new JsonExtractIn("not json", "a"),
            ExecutionMode.PREVIEW);
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("Invalid JSON");
  }

  @Test
  void returnsNotPresentForNullJson() {
    var ctx = contextFactory.of(new JsonExtractIn(null, "a"), ExecutionMode.PREVIEW);
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }

  @Test
  void returnsNotPresentForEmptyJson() {
    var ctx = contextFactory.of(new JsonExtractIn("", "a"), ExecutionMode.PREVIEW);
    Result<JsonExtractOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().present()).isFalse();
    assertThat(result.value().value()).isNull();
  }
}

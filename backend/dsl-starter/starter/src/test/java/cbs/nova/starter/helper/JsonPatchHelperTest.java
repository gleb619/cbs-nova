package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.JsonPatchIn;
import cbs.nova.starter.helper.model.JsonPatchOut;
import org.junit.jupiter.api.Test;

class JsonPatchHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final JsonPatchHelper helper = new JsonPatchHelper();

  @Test
  void applyReplacesKey() {
    assertThat(apply("{\"a\":1}", "{\"a\":2}")).isEqualTo("{\"a\":2}");
  }

  @Test
  void applyRecursivelyMergesNestedObjects() {
    String source = "{\"a\":{\"b\":1,\"c\":2}}";
    String patch = "{\"a\":{\"b\":10,\"d\":4}}";
    assertThat(apply(source, patch)).isEqualTo("{\"a\":{\"b\":10,\"c\":2,\"d\":4}}");
  }

  @Test
  void applyNullValueRemovesKey() {
    String source = "{\"a\":1,\"b\":2}";
    String patch = "{\"a\":null}";
    assertThat(apply(source, patch)).isEqualTo("{\"b\":2}");
  }

  @Test
  void applyEmptyPatchReturnsSourceUnchanged() {
    String source = "{\"a\":1,\"b\":2}";
    String patch = "{}";
    assertThat(apply(source, patch)).isEqualTo("{\"a\":1,\"b\":2}");
  }

  @Test
  void applyAddsNewKeyNotInSource() {
    String source = "{\"a\":1}";
    String patch = "{\"b\":2}";
    assertThat(apply(source, patch)).isEqualTo("{\"a\":1,\"b\":2}");
  }

  @Test
  void applyArrayLeafReplacedAsIs() {
    String source = "{\"a\":[1,2,3],\"b\":1}";
    String patch = "{\"a\":[4,5,6]}";
    assertThat(apply(source, patch)).isEqualTo("{\"a\":[4,5,6],\"b\":1}");
  }

  @Test
  void diffIdenticalSourceAndTargetIsEmpty() {
    String shared = "{\"a\":1,\"b\":2}";
    assertThat(diff(shared, shared)).isEqualTo("{}");
  }

  @Test
  void diffAddedKey() {
    assertThat(diff("{}", "{\"key\":\"value\"}")).isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  void diffModifiedValue() {
    assertThat(diff("{\"key\":\"old\"}", "{\"key\":\"new\"}")).isEqualTo("{\"key\":\"new\"}");
  }

  @Test
  void diffDeletedKey() {
    assertThat(diff("{\"key\":\"value\"}", "{}")).isEqualTo("{\"key\":null}");
  }

  @Test
  void diffNestedObjects() {
    String source = "{\"a\":{\"x\":1,\"keep\":42},\"b\":1}";
    String target = "{\"a\":{\"x\":2,\"y\":3,\"keep\":42},\"b\":2}";
    assertThat(diff(source, target))
            .isEqualTo("{\"a\":{\"x\":2,\"y\":3},\"b\":2}");
  }

  @Test
  void applyEmptySourceFails() {
    Result<JsonPatchOut> result = execute(new JsonPatchIn("", "{}", null, "apply"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jsonPatch.source is required");
  }

  @Test
  void applyNullSourceFails() {
    Result<JsonPatchOut> result = execute(new JsonPatchIn(null, "{}", null, "apply"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jsonPatch.source is required");
  }

  @Test
  void applyMalformedSourceFailsWithParseErrorHint() {
    Result<JsonPatchOut> result = execute(new JsonPatchIn("{\"a\":", "{}", null, "apply"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("jsonPatch: invalid JSON in source:");
  }

  @Test
  void applyNonObjectSourceFails() {
    Result<JsonPatchOut> result = execute(new JsonPatchIn("[1,2,3]", "{}", null, "apply"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jsonPatch requires object JSON, got: ARRAY");
  }

  @Test
  void applyMalformedPatchFails() {
    Result<JsonPatchOut> result = execute(new JsonPatchIn("{}", "{not json", null, "apply"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("jsonPatch: invalid JSON in patch:");
  }

  private String apply(String source, String patch) {
    return execute(new JsonPatchIn(source, patch, null, "apply")).value().result();
  }

  private String diff(String source, String target) {
    return execute(new JsonPatchIn(source, null, target, "diff")).value().result();
  }

  private Result<JsonPatchOut> execute(JsonPatchIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}

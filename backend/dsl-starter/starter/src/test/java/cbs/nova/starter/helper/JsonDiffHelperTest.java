package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.JsonDiffIn;
import cbs.nova.starter.helper.model.JsonDiffOut;
import cbs.nova.starter.helper.model.JsonDiffOut.Change;
import org.junit.jupiter.api.Test;

class JsonDiffHelperTest {

  private final JsonDiffHelper helper = new JsonDiffHelper();

  // --- identical -----------------------------------------------------------

  @Test
  void identicalEmptyObjects() {
    JsonDiffOut out = diff("{}", "{}");
    assertThat(out.identical()).isTrue();
    assertThat(out.changes()).isEmpty();
  }

  @Test
  void identicalPrimitives() {
    JsonDiffOut out = diff("{\"a\":1,\"b\":\"x\",\"c\":true}", "{\"a\":1,\"b\":\"x\",\"c\":true}");
    assertThat(out.identical()).isTrue();
    assertThat(out.changes()).isEmpty();
  }

  @Test
  void identicalNestedStructure() {
    String shared = "{\"a\":{\"b\":[1,2,{\"c\":3}]}}";
    JsonDiffOut out = diff(shared, shared);
    assertThat(out.identical()).isTrue();
    assertThat(out.changes()).isEmpty();
  }

  // --- nested add/remove/change --------------------------------------------

  @Test
  void nestedAddedKey() {
    JsonDiffOut out = diff("{\"a\":1}", "{\"a\":1,\"b\":2}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.b", "added", null, "2"));
  }

  @Test
  void nestedRemovedKey() {
    JsonDiffOut out = diff("{\"a\":1,\"b\":2}", "{\"a\":1}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.b", "removed", "2", null));
  }

  @Test
  void nestedChangedValue() {
    JsonDiffOut out = diff("{\"a\":{\"b\":1}}", "{\"a\":{\"b\":2}}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a.b", "changed", "1", "2"));
  }

  @Test
  void deeplyNestedAdded() {
    JsonDiffOut out = diff(
            "{\"a\":{\"b\":{\"c\":1}}}",
            "{\"a\":{\"b\":{\"c\":1,\"d\":2}}}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a.b.d", "added", null, "2"));
  }

  // --- arrays ---------------------------------------------------------------

  @Test
  void arraySameElements() {
    JsonDiffOut out = diff("[1,2,3]", "[1,2,3]");
    assertThat(out.identical()).isTrue();
  }

  @Test
  void arrayChangedIndex() {
    JsonDiffOut out = diff("[1,2,3]", "[1,9,3]");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$[1]", "changed", "2", "9"));
  }

  @Test
  void arrayLeftLonger() {
    JsonDiffOut out = diff("[1,2,3]", "[1,2]");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$[2]", "removed", "3", null));
  }

  @Test
  void arrayRightLonger() {
    JsonDiffOut out = diff("[1,2]", "[1,2,3]");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$[2]", "added", null, "3"));
  }

  @Test
  void arrayBothLongerAndChanged() {
    JsonDiffOut out = diff("[1,2]", "[1,9,3]");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$[1]", "changed", "2", "9"),
            new Change("$[2]", "added", null, "3"));
  }

  @Test
  void nestedArrayInsideObject() {
    JsonDiffOut out = diff(
            "{\"items\":[{\"id\":1},{\"id\":2}]}",
            "{\"items\":[{\"id\":1},{\"id\":9}]}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.items[1].id", "changed", "2", "9"));
  }

  // --- type change ----------------------------------------------------------

  @Test
  void typeChangeObjectToScalar() {
    JsonDiffOut out = diff("{\"a\":{\"x\":1}}", "{\"a\":\"hello\"}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a", "changed", "{\"x\":1}", "\"hello\""));
  }

  @Test
  void typeChangeArrayToObject() {
    JsonDiffOut out = diff("{\"a\":[1,2]}", "{\"a\":{\"x\":1}}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a", "changed", "[1,2]", "{\"x\":1}"));
  }

  // --- null vs missing ------------------------------------------------------

  @Test
  void nullPresentVsMissing() {
    JsonDiffOut out = diff("{\"a\":1}", "{\"a\":1,\"b\":null}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.b", "added", null, "null"));
  }

  @Test
  void nullRemovedDistinguished() {
    JsonDiffOut out = diff("{\"a\":null,\"b\":1}", "{\"b\":1}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a", "removed", "null", null));
  }

  @Test
  void nullChangedToScalar() {
    JsonDiffOut out = diff("{\"a\":null}", "{\"a\":5}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a", "changed", "null", "5"));
  }

  @Test
  void scalarChangedToNull() {
    JsonDiffOut out = diff("{\"a\":5}", "{\"a\":null}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a", "changed", "5", "null"));
  }

  // --- invalid input --------------------------------------------------------

  @Test
  void nullLeftFails() {
    Result<JsonDiffOut> result = execute(new JsonDiffIn(null, "{}"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jsonDiff.left is required");
  }

  @Test
  void blankLeftFails() {
    Result<JsonDiffOut> result = execute(new JsonDiffIn("  ", "{}"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jsonDiff.left is required");
  }

  @Test
  void nullRightFails() {
    Result<JsonDiffOut> result = execute(new JsonDiffIn("{}", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jsonDiff.right is required");
  }

  @Test
  void invalidLeftFailsWithHint() {
    Result<JsonDiffOut> result = execute(new JsonDiffIn("{\"a\":", "{}"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("jsonDiff: invalid JSON in left:");
  }

  @Test
  void invalidRightFailsWithHint() {
    Result<JsonDiffOut> result = execute(new JsonDiffIn("{}", "{nope"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageStartingWith("jsonDiff: invalid JSON in right:");
  }

  @Test
  void bothInvalidLeftCheckedFirst() {
    Result<JsonDiffOut> result = execute(new JsonDiffIn("{\"a\":", "{nope"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageStartingWith("jsonDiff: invalid JSON in left:");
  }

  // --- empty-object edge cases ----------------------------------------------

  @Test
  void emptyObjectVsPopulated() {
    JsonDiffOut out = diff("{}", "{\"a\":1,\"b\":2}");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$.a", "added", null, "1"),
            new Change("$.b", "added", null, "2"));
  }

  @Test
  void emptyArrayVsPopulated() {
    JsonDiffOut out = diff("[]", "[1,2]");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$[0]", "added", null, "1"),
            new Change("$[1]", "added", null, "2"));
  }

  @Test
  void emptyObjectVsEmptyArrayIsTypeChange() {
    JsonDiffOut out = diff("{}", "[]");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$", "changed", "{}", "[]"));
  }

  @Test
  void primitiveTopLevelChange() {
    JsonDiffOut out = diff("1", "2");
    assertThat(out.identical()).isFalse();
    assertThat(out.changes()).containsExactly(
            new Change("$", "changed", "1", "2"));
  }

  // --- helpers --------------------------------------------------------------

  private JsonDiffOut diff(String left, String right) {
    Result<JsonDiffOut> result = execute(new JsonDiffIn(left, right));
    assertThat(result.isSuccess())
            .as("left=%s right=%s", left, right)
            .isTrue();
    return result.value();
  }

  private Result<JsonDiffOut> execute(JsonDiffIn input) {
    var ctx = SimpleContext.<JsonDiffIn>builder()
            .mode(ExecutionMode.PREVIEW)
            .body(input)
            .build();
    return helper.execute(ctx);
  }
}

package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.MergeRecordsIn;
import cbs.nova.starter.helper.model.MergeRecordsOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergeRecordsHelperTest {

  private final MergeRecordsHelper helper = new MergeRecordsHelper();

  @Test
  void shallowMergeOverwritesTopLevelKeys() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("a", 1);
    first.put("b", 2);
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("b", 3);
    second.put("c", 4);

    Result<MergeRecordsOut> result = execute(
            new MergeRecordsIn(List.of(first, second), "shallow"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).containsExactly(
            Map.entry("a", 1), Map.entry("b", 3), Map.entry("c", 4));
  }

  @Test
  void shallowDefaultsWhenModeIsNull() {
    Map<String, Object> first = Map.of("a", 1);
    Map<String, Object> second = Map.of("a", 2);

    Result<MergeRecordsOut> result = execute(new MergeRecordsIn(List.of(first, second), null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).containsExactly(Map.entry("a", 2));
  }

  @Test
  void singleRecordReturnsEquivalentMap() {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("a", 1);
    record.put("b", List.of(1, 2));

    Result<MergeRecordsOut> result = execute(new MergeRecordsIn(List.of(record), null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).containsExactly(Map.entry("a", 1),
            Map.entry("b", List.of(1, 2)));
  }

  @Test
  void deepMergeRecursivelyMergesNestedMaps() {
    Map<String, Object> user1 = new LinkedHashMap<>();
    user1.put("name", "alice");
    user1.put("tags", List.of("a"));
    Map<String, Object> meta1 = new LinkedHashMap<>();
    meta1.put("version", 1);
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("user", user1);
    first.put("meta", meta1);

    Map<String, Object> user2 = new LinkedHashMap<>();
    user2.put("age", 30);
    user2.put("tags", List.of("b"));
    Map<String, Object> meta2 = new LinkedHashMap<>();
    meta2.put("version", 2);
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("user", user2);
    second.put("meta", meta2);

    Result<MergeRecordsOut> result = execute(new MergeRecordsIn(List.of(first, second), "deep"));

    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> merged = result.value().result();
    @SuppressWarnings("unchecked")
    Map<String, Object> user = (Map<String, Object>) merged.get("user");
    assertThat(user).containsExactly(
            Map.entry("name", "alice"), Map.entry("tags", List.of("b")), Map.entry("age", 30));
    assertThat(merged.get("meta")).isEqualTo(Map.of("version", 2));
  }

  @Test
  void deepMergeScalarWinsOverMap() {
    Map<String, Object> first = Map.of("payload", Map.of("x", 1));
    Map<String, Object> second = Map.of("payload", "plain-text");

    Result<MergeRecordsOut> result = execute(new MergeRecordsIn(List.of(first, second), "deep"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).containsExactly(Map.entry("payload", "plain-text"));
  }

  @Test
  void deepMergeReplacesLists() {
    Map<String, Object> first = Map.of("items", List.of(1, 2));
    Map<String, Object> second = Map.of("items", List.of(3, 4, 5));

    Result<MergeRecordsOut> result = execute(new MergeRecordsIn(List.of(first, second), "deep"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).containsExactly(Map.entry("items", List.of(3, 4, 5)));
  }

  @Test
  void nullRecordsFails() {
    Result<MergeRecordsOut> result = execute(new MergeRecordsIn(null, "shallow"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("records");
  }

  @Test
  void emptyRecordsFails() {
    Result<MergeRecordsOut> result = execute(new MergeRecordsIn(List.of(), "shallow"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("records");
  }

  @Test
  void unknownModeFails() {
    Result<MergeRecordsOut> result = execute(
            new MergeRecordsIn(List.of(Map.of("a", 1)), "nested"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessageContaining("mergeRecords.mode must be one of")
            .hasMessageContaining("nested");
  }

  private Result<MergeRecordsOut> execute(MergeRecordsIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}

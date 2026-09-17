package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.SortRecordsIn;
import cbs.nova.starter.helper.model.SortRecordsOut;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SortRecordsHelperTest {

  private final SortRecordsHelper helper = new SortRecordsHelper();

  @Test
  void sortsNumericFieldAscending() {
    var records = List.of(
            Map.<String, Object>of("a", 2),
            Map.<String, Object>of("a", 1),
            Map.<String, Object>of("a", 3));
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(records, "a", true, null, null)).mode(ExecutionMode.PREVIEW)
            .build();
    Result<SortRecordsOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().records())
            .map(r -> r.get("a"))
            .containsExactly(1, 2, 3);
  }

  @Test
  void sortsNumericFieldDescending() {
    var records = List.of(
            Map.<String, Object>of("a", 2),
            Map.<String, Object>of("a", 1),
            Map.<String, Object>of("a", 3));
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(records, "a", false, null, null)).mode(ExecutionMode.PREVIEW)
            .build();
    Result<SortRecordsOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().records())
            .map(r -> r.get("a"))
            .containsExactly(3, 2, 1);
  }

  @Test
  void sortsUsingDirectionParameter() {
    var records = List.of(
            Map.<String, Object>of("a", 2),
            Map.<String, Object>of("a", 1));
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(records, "a", true, null, "desc")).mode(ExecutionMode.PREVIEW)
            .build();
    Result<SortRecordsOut> result = helper.execute(ctx);
    assertThat(result.value().records())
            .map(r -> r.get("a"))
            .containsExactly(2, 1);
  }

  @Test
  void sortsStringFieldLexicographicallyWithStringAlgorithm() {
    var records = List.<Map<String, Object>>of(
            Map.of("name", "charlie"),
            Map.of("name", "alice"),
            Map.of("name", "bob"));
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(records, "name", true, "string", null))
            .mode(ExecutionMode.PREVIEW).build();
    Result<SortRecordsOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().records())
            .map(r -> r.get("name"))
            .containsExactly("alice", "bob", "charlie");
  }

  @Test
  void sortsNumericStringsWithNumericAlgorithm() {
    var records = List.<Map<String, Object>>of(
            Map.of("a", "100"),
            Map.of("a", "20"),
            Map.of("a", "3"));
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(records, "a", true, "numeric", null))
            .mode(ExecutionMode.PREVIEW).build();
    Result<SortRecordsOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().records())
            .map(r -> r.get("a"))
            .containsExactly("3", "20", "100");
  }

  @Test
  void returnsEmptyForNullRecords() {
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(null, "a", true, null, null)).mode(ExecutionMode.PREVIEW)
            .build();
    assertThat(helper.execute(ctx).value().records()).isEmpty();
  }

  @Test
  void returnsEmptyForEmptyRecords() {
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(List.of(), "a", true, null, null)).mode(ExecutionMode.PREVIEW)
            .build();
    assertThat(helper.execute(ctx).value().records()).isEmpty();
  }

  @Test
  void sortsWithNullValuesPlacedLast() {
    Map<String, Object> one = new HashMap<>();
    one.put("a", 3);
    Map<String, Object> two = new HashMap<>();
    two.put("a", null);
    Map<String, Object> three = new HashMap<>();
    three.put("a", 1);
    var records = List.of(one, two, three);
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(records, "a", true, null, null)).mode(ExecutionMode.PREVIEW)
            .build();
    Result<SortRecordsOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().records())
            .map(r -> r.get("a"))
            .containsExactly(1, 3, null);
  }

  @Test
  void sortsMixedTypesByStringCoercion() {
    var records = List.<Map<String, Object>>of(
            Map.of("a", 100),
            Map.of("a", "20"),
            Map.of("a", 3));
    var ctx = SimpleContext.<SortRecordsIn>builder()
            .body(new SortRecordsIn(records, "a", true, null, null)).mode(ExecutionMode.PREVIEW)
            .build();
    Result<SortRecordsOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().records())
            .map(r -> String.valueOf(r.get("a")))
            .containsExactly("100", "20", "3");
  }
}

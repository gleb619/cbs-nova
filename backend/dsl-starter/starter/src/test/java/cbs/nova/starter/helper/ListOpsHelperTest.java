package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.ListOpsIn;
import cbs.nova.starter.helper.model.ListOpsOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListOpsHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ListOpsHelper helper = new ListOpsHelper();

  @Test
  void pluckExtractsFieldAcrossMixedRecords() {
    List<Map<String, Object>> records = List.of(
            Map.of("id", 1, "name", "alice"),
            Map.of("id", 2, "name", "bob"),
            Map.of("id", 3, "name", "carol"));
    Result<ListOpsOut> result = execute(new ListOpsIn("pluck", records, null, null, "name", null));
    assertThat(result.isSuccess()).isTrue();
    assertThat((List<Object>) result.value().result()).containsExactly("alice", "bob", "carol");
  }

  @Test
  void pluckMissingFieldOnOneRecordReportsIndex() {
    List<Map<String, Object>> records = List.of(
            Map.of("id", 1, "name", "alice"),
            Map.of("id", 2),
            Map.of("id", 3, "name", "carol"));
    Result<ListOpsOut> result = execute(new ListOpsIn("pluck", records, null, null, "name", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("index 1");
  }

  @Test
  void flattenOneLevelDefaultFlattensNestedLists() {
    List<Object> nested = List.of(
            List.of(1, 2),
            List.of(3, 4),
            List.of(5, 6));
    Result<ListOpsOut> result = execute(new ListOpsIn("flatten", null, null, nested, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat((List<Object>) result.value().result()).containsExactly(1, 2, 3, 4, 5, 6);
  }

  @Test
  void flattenDepthNegativeOneFullyFlattensArbitrarilyNestedLists() {
    List<Object> nested = List.of(
            1,
            List.of(2, List.of(3, List.of(4, List.of(5, 6))), 7),
            8);
    Result<ListOpsOut> result = execute(new ListOpsIn("flatten", null, null, nested, null, -1));
    assertThat(result.isSuccess()).isTrue();
    assertThat((List<Object>) result.value().result()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
  }

  @Test
  void distinctPreservesInsertionOrder() {
    List<Object> list = List.of("b", "a", "b", "c", "a", "d");
    Result<ListOpsOut> result = execute(new ListOpsIn("distinct", null, list, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat((List<Object>) result.value().result()).containsExactly("b", "a", "c", "d");
  }

  @Test
  void distinctDeduplicatesAcrossMixedTypes() {
    Map<String, Object> map1 = new LinkedHashMap<>();
    map1.put("k", 1);
    Map<String, Object> map2 = new LinkedHashMap<>();
    map2.put("k", 2);
    List<Object> list = List.of("x", 1, "x", map1, 1, map1, 2, map2, "y");
    Result<ListOpsOut> result = execute(new ListOpsIn("distinct", null, list, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat((List<Object>) result.value().result()).containsExactly("x", 1, map1, 2, map2, "y");
  }

  @Test
  void groupByPreservesFirstSeenGroupOrder() {
    List<Map<String, Object>> records = List.of(
            Map.of("dept", "eng", "name", "alice"),
            Map.of("dept", "sales", "name", "bob"),
            Map.of("dept", "eng", "name", "carol"),
            Map.of("dept", "hr", "name", "dave"),
            Map.of("dept", "sales", "name", "eve"));
    Result<ListOpsOut> result = execute(
            new ListOpsIn("groupBy", records, null, null, "dept", null));
    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<Object, List<Map<String, Object>>> groups = (Map<Object, List<Map<String, Object>>>) result
            .value().result();
    assertThat(groups.keySet()).containsExactly("eng", "sales", "hr");
    assertThat(groups.get("eng")).hasSize(2);
    assertThat(groups.get("sales")).hasSize(2);
    assertThat(groups.get("hr")).hasSize(1);
  }

  @Test
  void countByReportsFrequencyCounts() {
    List<Map<String, Object>> records = List.of(
            Map.of("color", "red"),
            Map.of("color", "blue"),
            Map.of("color", "red"),
            Map.of("color", "green"),
            Map.of("color", "red"),
            Map.of("color", "blue"));
    Result<ListOpsOut> result = execute(
            new ListOpsIn("countBy", records, null, null, "color", null));
    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<Object, Long> counts = (Map<Object, Long>) result.value().result();
    assertThat(counts).containsExactly(Map.entry("red", 3L), Map.entry("blue", 2L),
            Map.entry("green", 1L));
  }

  @Test
  void sumByComputesNumericSum() {
    List<Map<String, Object>> records = List.of(
            Map.of("amount", 10),
            Map.of("amount", 20.5),
            Map.of("amount", 5));
    Result<ListOpsOut> result = execute(
            new ListOpsIn("sumBy", records, null, null, "amount", null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo(35.5);
  }

  @Test
  void sumByNonNumericFieldFails() {
    List<Map<String, Object>> records = List.of(
            Map.of("amount", 10),
            Map.of("amount", "twenty"),
            Map.of("amount", 5));
    Result<ListOpsOut> result = execute(
            new ListOpsIn("sumBy", records, null, null, "amount", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("non-numeric");
    assertThat(result.cause()).hasMessageContaining("index 1");
  }

  @Test
  void minByPicksRecordWithSmallestFieldValue() {
    List<Map<String, Object>> records = List.of(
            Map.of("score", 5),
            Map.of("score", 1),
            Map.of("score", 9),
            Map.of("score", 3));
    Result<ListOpsOut> result = execute(new ListOpsIn("minBy", records, null, null, "score", null));
    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> winner = (Map<String, Object>) result.value().result();
    assertThat(winner).containsEntry("score", 1);
  }

  @Test
  void maxByPicksRecordWithLargestFieldValue() {
    List<Map<String, Object>> records = List.of(
            Map.of("score", 5),
            Map.of("score", 1),
            Map.of("score", 9),
            Map.of("score", 3));
    Result<ListOpsOut> result = execute(new ListOpsIn("maxBy", records, null, null, "score", null));
    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> winner = (Map<String, Object>) result.value().result();
    assertThat(winner).containsEntry("score", 9);
  }

  @Test
  void minByTieBrokenByFirstSeenRecord() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("score", 7);
    first.put("id", "first");
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("score", 7);
    second.put("id", "second");
    List<Map<String, Object>> records = List.of(first, second);
    Result<ListOpsOut> result = execute(new ListOpsIn("minBy", records, null, null, "score", null));
    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> winner = (Map<String, Object>) result.value().result();
    assertThat(winner).containsEntry("id", "first");
  }

  @Test
  void sumByEmptyRecordsFails() {
    Result<ListOpsOut> result = execute(
            new ListOpsIn("sumBy", List.of(), null, null, "amount", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("empty");
  }

  @Test
  void minByEmptyRecordsFails() {
    Result<ListOpsOut> result = execute(
            new ListOpsIn("minBy", List.of(), null, null, "score", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("empty");
  }

  @Test
  void maxByEmptyRecordsFails() {
    Result<ListOpsOut> result = execute(
            new ListOpsIn("maxBy", List.of(), null, null, "score", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("empty");
  }

  @Test
  void unknownModeFails() {
    Result<ListOpsOut> result = execute(
            new ListOpsIn("frobnicate", List.of(), null, null, "x", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessageContaining("listOps.mode must be one of")
            .hasMessageContaining("frobnicate");
  }

  private Result<ListOpsOut> execute(ListOpsIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}

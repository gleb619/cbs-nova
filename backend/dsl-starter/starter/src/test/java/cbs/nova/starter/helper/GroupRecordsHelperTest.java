package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.GroupRecordsIn;
import cbs.nova.starter.helper.model.GroupRecordsOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroupRecordsHelperTest {

  private final GroupRecordsHelper helper = new GroupRecordsHelper();

  @Test
  void groupsBySingleKey() {
    var records = List.<Map<String, Object>>of(
            Map.of("category", "a", "value", 1),
            Map.of("category", "b", "value", 2),
            Map.of("category", "a", "value", 3));

    Result<GroupRecordsOut> result = execute(new GroupRecordsIn(records, "category"));

    assertThat(result.isSuccess()).isTrue();
    Map<Object, List<Map<String, Object>>> groups = result.value().groups();
    assertThat(groups).containsOnlyKeys("a", "b");
    assertThat(groups.get("a"))
            .map(r -> r.get("value"))
            .containsExactly(1, 3);
    assertThat(groups.get("b"))
            .map(r -> r.get("value"))
            .containsExactly(2);
  }

  @Test
  void groupsByNestedKeyPath() {
    Map<String, Object> meta1 = new LinkedHashMap<>();
    meta1.put("tenant", "t1");
    Map<String, Object> meta2 = new LinkedHashMap<>();
    meta2.put("tenant", "t2");
    Map<String, Object> meta3 = new LinkedHashMap<>();
    meta3.put("tenant", "t1");

    var records = List.<Map<String, Object>>of(
            Map.of("meta", meta1, "name", "r1"),
            Map.of("meta", meta2, "name", "r2"),
            Map.of("meta", meta3, "name", "r3"));

    Result<GroupRecordsOut> result = execute(new GroupRecordsIn(records, "meta.tenant"));

    assertThat(result.isSuccess()).isTrue();
    Map<Object, List<Map<String, Object>>> groups = result.value().groups();
    assertThat(groups).containsOnlyKeys("t1", "t2");
    assertThat(groups.get("t1")).map(r -> r.get("name")).containsExactly("r1", "r3");
    assertThat(groups.get("t2")).map(r -> r.get("name")).containsExactly("r2");
  }

  @Test
  void groupsByCompositeKeyList() {
    var records = List.<Map<String, Object>>of(
            Map.of("tenant", "t1", "env", "prod", "name", "a"),
            Map.of("tenant", "t1", "env", "dev", "name", "b"),
            Map.of("tenant", "t1", "env", "prod", "name", "c"));

    Result<GroupRecordsOut> result = execute(new GroupRecordsIn(records, List.of("tenant", "env")));

    assertThat(result.isSuccess()).isTrue();
    Map<Object, List<Map<String, Object>>> groups = result.value().groups();
    assertThat(groups).containsOnlyKeys(List.of("t1", "prod"), List.of("t1", "dev"));
    assertThat(groups.get(List.of("t1", "prod")))
            .map(r -> r.get("name"))
            .containsExactly("a", "c");
    assertThat(groups.get(List.of("t1", "dev")))
            .map(r -> r.get("name"))
            .containsExactly("b");
  }

  @Test
  void missingOrNullKeyLandsUnderSentinel() {
    Map<String, Object> nullCategory = new LinkedHashMap<>();
    nullCategory.put("category", null);
    nullCategory.put("name", "null-category");
    Map<String, Object> missingCategory = new LinkedHashMap<>();
    missingCategory.put("name", "missing-category");

    var records = List.<Map<String, Object>>of(
            Map.of("category", "a", "name", "ok"),
            nullCategory,
            missingCategory);

    Result<GroupRecordsOut> result = execute(new GroupRecordsIn(records, "category"));

    assertThat(result.isSuccess()).isTrue();
    Map<Object, List<Map<String, Object>>> groups = result.value().groups();
    assertThat(groups).containsOnlyKeys("a", "__null__");
    assertThat(groups.get("a")).map(r -> r.get("name")).containsExactly("ok");
    assertThat(groups.get("__null__"))
            .map(r -> r.get("name"))
            .containsExactly("null-category", "missing-category");
  }

  @Test
  void emptyRecordsReturnsEmptyMap() {
    Result<GroupRecordsOut> result = execute(new GroupRecordsIn(List.of(), "category"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().groups()).isEmpty();
  }

  @Test
  void nullRecordsReturnsEmptyMap() {
    Result<GroupRecordsOut> result = execute(new GroupRecordsIn(null, "category"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().groups()).isEmpty();
  }

  @Test
  void preservesInsertionOrderWithinGroups() {
    var records = List.<Map<String, Object>>of(
            Map.of("g", "x", "seq", 1),
            Map.of("g", "y", "seq", 2),
            Map.of("g", "x", "seq", 3),
            Map.of("g", "y", "seq", 4),
            Map.of("g", "x", "seq", 5));

    Result<GroupRecordsOut> result = execute(new GroupRecordsIn(records, "g"));

    assertThat(result.value().groups().get("x"))
            .map(r -> r.get("seq"))
            .containsExactly(1, 3, 5);
    assertThat(result.value().groups().get("y"))
            .map(r -> r.get("seq"))
            .containsExactly(2, 4);
  }

  private Result<GroupRecordsOut> execute(GroupRecordsIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}

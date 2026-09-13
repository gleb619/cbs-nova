package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.PickIn;
import cbs.nova.starter.helper.model.PickOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PickHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final PickHelper helper = new PickHelper();

  // --- pick mode ---

  @Test
  void pickKeepsOnlyListedKeys() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> result = execute(new PickIn(source, List.of("id", "email"), "pick"));
    assertThat(result).containsExactly(
            Map.entry("id", 7),
            Map.entry("email", "ada@example.com"));
  }

  @Test
  void pickSkipsMissingKeysWithoutNullFilling() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> result = execute(new PickIn(source, List.of("email", "missing"), "pick"));
    assertThat(result).containsExactly(Map.entry("email", "ada@example.com"));
    assertThat(result).doesNotContainKey("missing");
  }

  @Test
  void pickOrderFollowsKeysParamOrderNotSourceOrder() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> result = execute(
            new PickIn(source, List.of("email", "id", "name"), "pick"));
    assertThat(result.keySet()).containsExactly("email", "id", "name");
  }

  @Test
  void pickDefaultsToPickModeWhenModeNullOrBlank() {
    Map<String, Object> source = sourceMap();
    assertThat(execute(new PickIn(source, List.of("id"), null)))
            .containsExactly(Map.entry("id", 7));
    assertThat(execute(new PickIn(source, List.of("id"), "  ")))
            .containsExactly(Map.entry("id", 7));
    assertThat(execute(new PickIn(source, List.of("id"), "PICK")))
            .containsExactly(Map.entry("id", 7));
  }

  @Test
  void pickKeepsPresentButNullValues() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> result = execute(new PickIn(source, List.of("deletedAt"), "pick"));
    assertThat(result).containsEntry("deletedAt", null);
    assertThat(result).hasSize(1);
  }

  @Test
  void pickDoesNotMutateSource() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> snapshot = new LinkedHashMap<>(source);
    execute(new PickIn(source, List.of("id"), "pick"));
    assertThat(source).containsExactlyEntriesOf(snapshot);
  }

  // --- omit mode ---

  @Test
  void omitDropsListedKeysAndPreservesSourceOrder() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> result = execute(new PickIn(source, List.of("email"), "omit"));
    assertThat(result.keySet()).containsExactly("id", "name", "deletedAt");
  }

  @Test
  void omitOfAbsentKeyIsANoOp() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> result = execute(new PickIn(source, List.of("missing"), "omit"));
    assertThat(result).containsExactlyEntriesOf(source);
  }

  @Test
  void omitRemovesPresentButNullValuesWithTheKey() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> result = execute(new PickIn(source, List.of("deletedAt"), "omit"));
    assertThat(result).doesNotContainKey("deletedAt");
    assertThat(result.keySet()).containsExactly("id", "name", "email");
  }

  @Test
  void omitDoesNotMutateSource() {
    Map<String, Object> source = sourceMap();
    Map<String, Object> snapshot = new LinkedHashMap<>(source);
    execute(new PickIn(source, List.of("email", "deletedAt"), "omit"));
    assertThat(source).containsExactlyEntriesOf(snapshot);
  }

  // --- validation failures ---

  @Test
  void nullSourceFails() {
    Result<PickOut> result = helper.execute(
            contextFactory.of(new PickIn(null, List.of("id"), "pick"), ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("pick.source is required");
  }

  @Test
  void emptySourceYieldsEmptyResult() {
    Map<String, Object> result = execute(new PickIn(Map.of(), List.of("id"), "pick"));
    assertThat(result).isEmpty();
  }

  @Test
  void nullKeysFail() {
    Result<PickOut> result = helper.execute(
            contextFactory.of(new PickIn(sourceMap(), null, "pick"), ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("pick.keys must be non-empty");
  }

  @Test
  void emptyKeysListFails() {
    Result<PickOut> result = helper.execute(contextFactory.of(
            new PickIn(sourceMap(), List.of(), "pick"), ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("pick.keys must be non-empty");
  }

  @Test
  void unknownModeFails() {
    Result<PickOut> result = helper.execute(contextFactory.of(
            new PickIn(sourceMap(), List.of("id"), "rename"), ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- helpers ---------------------------------------------------------

  private static Map<String, Object> sourceMap() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("id", 7);
    source.put("name", "Ada");
    source.put("email", "ada@example.com");
    source.put("deletedAt", null);
    return source;
  }

  private Map<String, Object> execute(PickIn input) {
    Result<PickOut> result = helper.execute(
            contextFactory.of(input, ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isTrue();
    return result.value().result();
  }
}

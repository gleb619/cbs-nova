package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.PickIn;
import cbs.nova.starter.helper.model.PickOut;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PickHelperTest {

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
            SimpleContext.<PickIn>builder().body(new PickIn(null, List.of("id"), "pick"))
                    .mode(ExecutionMode.PREVIEW).build());
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
            SimpleContext.<PickIn>builder().body(new PickIn(sourceMap(), null, "pick"))
                    .mode(ExecutionMode.PREVIEW).build());
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("pick.keys must be non-empty");
  }

  @Test
  void emptyKeysListFails() {
    Result<PickOut> result = helper.execute(SimpleContext.<PickIn>builder()
            .body(new PickIn(sourceMap(), List.of(), "pick")).mode(ExecutionMode.PREVIEW).build());
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("pick.keys must be non-empty");
  }

  @Test
  void unknownModeFails() {
    Result<PickOut> result = helper.execute(
            SimpleContext.<PickIn>builder().body(new PickIn(sourceMap(), List.of("id"), "rename"))
                    .mode(ExecutionMode.PREVIEW).build());
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- pick dotted paths ---

  @Test
  void pickNestedPathRebuildsNestedStructure() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source, List.of("customer.address.city"), "pick"));
    assertThat(result).hasSize(1);
    assertThat(result).containsKey("customer");
    assertThat(result.get("customer")).isInstanceOf(Map.class);
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    assertThat(customer).containsOnlyKeys("address");
    Map<String, Object> address = (Map<String, Object>) customer.get("address");
    assertThat(address).containsOnly(Map.entry("city", "Berlin"));
  }

  @Test
  void pickMultiplePathsSharingPrefixMergeIntoOneNestedMap() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("customer.address.city", "customer.address.zip", "customer.email"),
            "pick"));
    assertThat(result).containsOnlyKeys("customer");
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    assertThat(customer.keySet()).containsExactly("address", "email");
    Map<String, Object> address = (Map<String, Object>) customer.get("address");
    assertThat(address).containsOnly(
            Map.entry("city", "Berlin"),
            Map.entry("zip", "10115"));
    assertThat(customer.get("email")).isEqualTo("ada@example.com");
  }

  @Test
  void pickSiblingKeyAtPrefixLevelIsNotIncluded() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("customer.address.city"), "pick"));
    // 'name' sits at top level next to 'customer' — it must stay out of the result.
    assertThat(result).doesNotContainKey("name");
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    assertThat(customer).doesNotContainKey("name");
    assertThat(customer).doesNotContainKey("email");
  }

  @Test
  void pickDottedPathOrderFollowsKeysParamOrder() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("customer.email", "customer.address.city"), "pick"));
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    assertThat(customer.keySet()).containsExactly("email", "address");
  }

  @Test
  void pickDottedPathAbsentAtAnyLevelIsSilentlySkipped() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("customer.address.country.iso"), "pick"));
    // 'iso' does not exist under customer.address.country — skipped silently.
    assertThat(result).isEmpty();
  }

  @Test
  void pickDottedPathAbsentTopLevelSegmentIsSilentlySkipped() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("auth.token"), "pick"));
    assertThat(result).isEmpty();
  }

  @Test
  void pickDottedPathKeepsPresentButNullLeaf() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("customer.address.country"), "pick"));
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    Map<String, Object> address = (Map<String, Object>) customer.get("address");
    assertThat(address).containsEntry("country", null);
  }

  @Test
  void pickDottedPathThroughNonMapIntermediateIsSkipped() {
    Map<String, Object> source = nestedSource();
    // 'tags' is a List — pick[1] cannot traverse it; the leaf is skipped (treated as absent).
    Map<String, Object> result = execute(new PickIn(source,
            List.of("tags.first"), "pick"));
    assertThat(result).isEmpty();
  }

  @Test
  void pickFlatKeyContainingDotWinsOverPathSplit() {
    // If 'a.b' exists as a literal flat key, it wins — existing flat callers unaffected.
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("a.b", "flat-wins");
    source.put("a", new LinkedHashMap<>(Map.of("b", "nested-value")));
    Map<String, Object> result = execute(new PickIn(source, List.of("a.b"), "pick"));
    assertThat(result).containsExactly(Map.entry("a.b", "flat-wins"));
  }

  @Test
  void pickDottedPathDoesNotMutateSource() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> snapshot = deepCopy(source);
    execute(new PickIn(source,
            List.of("customer.address.city", "customer.email"), "pick"));
    assertThat(source).isEqualTo(snapshot);
  }

  // --- omit dotted paths ---

  @Test
  void omitRemovesLeafAlongDottedPathAndPreservesSiblings() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("customer.address.city"), "omit"));
    // Top-level untouched, sibling 'address' map still present with 'zip' (and 'country' null).
    assertThat(result.keySet()).containsExactly("id", "name", "customer", "tags");
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    assertThat(customer.keySet()).containsExactly("email", "address");
    Map<String, Object> address = (Map<String, Object>) customer.get("address");
    assertThat(address.keySet()).containsExactly("zip", "country");
    assertThat(address).doesNotContainKey("city");
  }

  @Test
  void omitDottedPathOfAbsentTopLevelSegmentIsNoOp() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source, List.of("auth.token"), "omit"));
    assertThat(result).isEqualTo(source);
    assertThat(result.keySet()).containsExactly("id", "name", "customer", "tags");
  }

  @Test
  void omitDottedPathThroughAbsentLeafIsNoOp() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("customer.address.country.iso"), "omit"));
    // Absent leaf — no-op. The address map (and its other keys) is shared by reference.
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    assertThat(customer.get("address")).isSameAs(source.get("customer") != null
            ? ((Map<String, Object>) ((Map<String, Object>) source.get("customer")).get("address"))
            : null);
  }

  @Test
  void omitDottedPathThroughNonMapIntermediateIsNoOp() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source, List.of("tags.first"), "omit"));
    // 'tags' is a List — no-op, source map structure untouched.
    assertThat(result).isEqualTo(source);
  }

  @Test
  void omitDottedPathDoesNotMutateSource() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> snapshot = deepCopy(source);
    execute(new PickIn(source, List.of("customer.address.city"), "omit"));
    assertThat(source).isEqualTo(snapshot);
  }

  @Test
  void omitMixesFlatAndDeepKeys() {
    Map<String, Object> source = nestedSource();
    Map<String, Object> result = execute(new PickIn(source,
            List.of("id", "customer.address.city"), "omit"));
    assertThat(result).doesNotContainKey("id");
    Map<String, Object> customer = (Map<String, Object>) result.get("customer");
    Map<String, Object> address = (Map<String, Object>) customer.get("address");
    assertThat(address).doesNotContainKey("city");
    assertThat(address).containsKey("zip");
  }

  @Test
  void omitFlatKeyContainingDotTakesPrecedenceOverPathSplit() {
    // If 'a.b' is a literal flat key, the flat omit path removes it; nothing else happens.
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("a.b", "flat-value");
    Map<String, Object> nested = new LinkedHashMap<>(Map.of("b", "nested-value"));
    source.put("a", nested);
    Map<String, Object> result = execute(new PickIn(source, List.of("a.b"), "omit"));
    assertThat(result).doesNotContainKey("a.b");
    // The 'a' map and its 'b' leaf are untouched (path-split omitted).
    Map<String, Object> aMap = (Map<String, Object>) result.get("a");
    assertThat(aMap).containsExactly(Map.entry("b", "nested-value"));
  }

  // --- helpers ---------------------------------------------------------

  private static Map<String, Object> deepCopy(Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : source.entrySet()) {
      Object v = e.getValue();
      if (v instanceof Map<?, ?>) {
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) v;
        copy.put(e.getKey(), deepCopy(inner));
      } else if (v instanceof List<?> list) {
        copy.put(e.getKey(), new java.util.ArrayList<>(list));
      } else {
        copy.put(e.getKey(), v);
      }
    }
    return copy;
  }

  private static Map<String, Object> nestedSource() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("id", 7);
    source.put("name", "Ada");
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("city", "Berlin");
    address.put("zip", "10115");
    address.put("country", null);
    Map<String, Object> customer = new LinkedHashMap<>();
    customer.put("email", "ada@example.com");
    customer.put("address", address);
    source.put("customer", customer);
    source.put("tags", List.of("vip", "eu"));
    return source;
  }

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
            SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build());
    assertThat(result.isSuccess()).isTrue();
    return result.value().result();
  }
}

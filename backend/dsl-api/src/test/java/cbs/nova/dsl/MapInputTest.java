package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.model.MapInput;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class MapInputTest {

  @Test
  void emptyReturnsEmptyMap() {
    var input = MapInput.of();
    assertThat(input.values()).isEmpty();
  }

  @Test
  void singlePairReturnsMap() {
    var input = MapInput.of("key", "value");
    assertThat(input.values()).hasSize(1).containsEntry("key", "value");
  }

  @Test
  void multiplePairsPreservesInsertionOrder() {
    var input = MapInput.of("a", 1, "b", 2, "c", 3);
    assertThat(input.values().keySet()).containsExactly("a", "b", "c");
    assertThat(input.values().values()).containsExactly(1, 2, 3);
  }

  @Test
  void nullValueAllowed() {
    var input = MapInput.of("key", null);
    assertThat(input.values()).containsEntry("key", null);
  }

  @Test
  void oddCountThrows() {
    assertThatThrownBy(() -> MapInput.of("key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("even number");
  }

  @Test
  void nonStringKeyThrows() {
    assertThatThrownBy(() -> MapInput.of(42, "value"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Strings");
  }

  @Test
  void returnedValuesMapIsUnmodifiable() {
    var input = MapInput.of("k", "v");
    assertThatThrownBy(() -> input.values().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void fromMapCopiesValues() {
    var source = new LinkedHashMap<String, Object>();
    source.put("a", 1);
    var input = MapInput.fromMap(source);
    source.put("b", 2);
    assertThat(input.values()).containsOnlyKeys("a");
  }

  @Test
  void asMapReturnsMutableCopy() {
    var input = MapInput.of("k", "v");
    var copy = input.asMap();
    assertThat(copy).containsEntry("k", "v");
    copy.put("x", "y");
    assertThat(input.values()).doesNotContainKey("x");
  }
}

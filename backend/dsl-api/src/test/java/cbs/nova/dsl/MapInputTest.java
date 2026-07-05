package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MapInputTest {

  @Test
  void emptyReturnsEmptyMap() {
    assertThat(MapInput.of()).isEmpty();
  }

  @Test
  void singlePairReturnsMap() {
    var map = MapInput.of("key", "value");
    assertThat(map).hasSize(1).containsEntry("key", "value");
  }

  @Test
  void multiplePairsPreservesInsertionOrder() {
    var map = MapInput.of("a", 1, "b", 2, "c", 3);
    assertThat(map.keySet()).containsExactly("a", "b", "c");
    assertThat(map.values()).containsExactly(1, 2, 3);
  }

  @Test
  void nullValueAllowed() {
    var map = MapInput.of("key", null);
    assertThat(map).containsEntry("key", null);
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
  void returnedMapIsUnmodifiable() {
    var map = MapInput.of("k", "v");
    assertThatThrownBy(() -> map.put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
  }
}

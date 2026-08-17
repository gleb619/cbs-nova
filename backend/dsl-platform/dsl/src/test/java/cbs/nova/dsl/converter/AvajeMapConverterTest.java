package cbs.nova.dsl.converter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.registry.ModelRegistry;
import io.avaje.jsonb.Json;
import io.avaje.jsonb.Jsonb;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

public class AvajeMapConverterTest {

  @Json
  public record Point(int x, String label) {
  }

  @Test
  void fromMapUsesFastPathForRegisteredTypes() {
    var converter = new AvajeMapConverter(
            Jsonb.builder().build(), registryOf(Point.class));

    Map<String, Object> map = Map.of("x", 1, "label", "a");
    Point point = converter.fromMap(map, Point.class);

    assertThat(point).isEqualTo(new Point(1, "a"));
    assertThat(converter.supports(Point.class)).isTrue();
  }

  @Test
  void fromMapFallsBackForUnregisteredTypes() {
    var converter = new AvajeMapConverter(Jsonb.builder().build(), registryOf());

    Map<String, Object> map = Map.of("x", 2, "label", "b");
    Point point = converter.fromMap(map, Point.class);

    assertThat(point).isEqualTo(new Point(2, "b"));
    assertThat(converter.supports(Point.class)).isFalse();
  }

  @Test
  void toMapConvertsRecordToMap() {
    var converter = new AvajeMapConverter(Jsonb.builder().build(), registryOf());

    Map<String, Object> map = converter.toMap(new Point(3, "c"));

    assertThat(map).containsEntry("x", 3L).containsEntry("label", "c");
  }

  @Test
  void toMapReturnsMapAsIs() {
    var converter = new AvajeMapConverter(Jsonb.builder().build(), registryOf());

    Map<String, Object> map = Map.of("k", "v");
    assertThat(converter.toMap(map)).isSameAs(map);
  }

  private static ModelRegistry registryOf(Class<?>... types) {
    var set = Set.of(types);
    return new ModelRegistry() {
      @Override
      public @NonNull Set<Class<?>> modelTypes() {
        return set;
      }
    };
  }
}

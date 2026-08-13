package cbs.nova.dsl.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.MapInput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

class MapInputConverterTest {

  private final MapInputConverter converter = new MapInputConverter();

  record Address(String city, int zip) {
  }

  record Person(String name, Status status, Address address) {
  }

  enum Status {
    ACTIVE, INACTIVE
  }

  @Test
  void nullValueReturnsNull() {
    assertThat(converter.convert(null, String.class)).isNull();
    assertThat(converter.convert(null, parameterizedType(List.class, String.class))).isNull();
  }

  @Test
  void mapInputTargetPassthrough() {
    MapInput existing = MapInput.of("key", "value");
    assertThat(converter.convert(existing, MapInput.class)).isSameAs(existing);
  }

  @Test
  void mapInputTargetWrapsMap() {
    Map<String, Object> map = Map.of("a", 1, "b", "two");
    assertThat(converter.convert(map, MapInput.class)).isEqualTo(MapInput.fromMap(map));
  }

  @Test
  void mapInputTargetReturnsNonMapUnchanged() {
    assertThat(converter.convert("plain", MapInput.class)).isEqualTo("plain");
    assertThat(converter.convert(42, MapInput.class)).isEqualTo(42);
  }

  @Test
  void alreadyInstanceClassTargetPassthrough() {
    String s = "value";
    assertThat(converter.convert(s, String.class)).isSameAs(s);

    Integer i = 7;
    assertThat(converter.convert(i, Integer.class)).isSameAs(i);
  }

  @Test
  void stringTargetConvertsViaValueOf() {
    assertThat(converter.convert(42, String.class)).isEqualTo("42");
    assertThat(converter.convert(true, String.class)).isEqualTo("true");
  }

  @Test
  void booleanTargetParsesString() {
    assertThat(converter.convert("true", boolean.class)).isEqualTo(true);
    assertThat(converter.convert("false", Boolean.class)).isEqualTo(false);
  }

  @Test
  void byteTargetNarrowsNumber() {
    assertThat(converter.convert(42, byte.class)).isEqualTo((byte) 42);
    assertThat(converter.convert(42, Byte.class)).isEqualTo(Byte.valueOf((byte) 42));
  }

  @Test
  void shortTargetNarrowsNumber() {
    assertThat(converter.convert(42, short.class)).isEqualTo((short) 42);
    assertThat(converter.convert(42, Short.class)).isEqualTo(Short.valueOf((short) 42));
  }

  @Test
  void intTargetNarrowsNumber() {
    assertThat(converter.convert(3L, int.class)).isEqualTo(3);
    assertThat(converter.convert(3L, Integer.class)).isEqualTo(3);
    assertThat(converter.convert(3.9d, int.class)).isEqualTo(3);
  }

  @Test
  void longTargetNarrowsNumber() {
    assertThat(converter.convert(3, long.class)).isEqualTo(3L);
    assertThat(converter.convert(3, Long.class)).isEqualTo(3L);
  }

  @Test
  void floatTargetNarrowsNumber() {
    assertThat(converter.convert(1.5d, float.class)).isEqualTo(1.5f);
    assertThat(converter.convert(1.5d, Float.class)).isEqualTo(1.5f);
  }

  @Test
  void doubleTargetNarrowsNumber() {
    assertThat(converter.convert(1.5f, double.class)).isEqualTo(1.5d);
    assertThat(converter.convert(1.5f, Double.class)).isEqualTo(1.5d);
  }

  @Test
  void charTargetTakesFirstCharacter() {
    assertThat(converter.convert("A", char.class)).isEqualTo('A');
    assertThat(converter.convert("Apple", Character.class)).isEqualTo('A');
  }

  @Test
  void enumTargetUsesValueOf() {
    assertThat(converter.convert("ACTIVE", Status.class)).isEqualTo(Status.ACTIVE);
  }

  @Test
  void arrayTargetFromCollection() {
    int[] result = (int[]) converter.convert(List.of(1, 2, 3), int[].class);
    assertThat(result).containsExactly(1, 2, 3);
  }

  @Test
  void arrayTargetConvertsElements() {
    String[] result = (String[]) converter.convert(List.of(1, 2), String[].class);
    assertThat(result).containsExactly("1", "2");
  }

  @Test
  void recordTargetFromMapRecursively() {
    Map<String, Object> addressMap = Map.of("city", "Aktobe", "zip", 12345);
    Map<String, Object> personMap = Map.of("name", "Joe", "status", "ACTIVE", "address",
            addressMap);

    Person person = (Person) converter.convert(personMap, Person.class);

    assertThat(person.name()).isEqualTo("Joe");
    assertThat(person.status()).isEqualTo(Status.ACTIVE);
    assertThat(person.address()).isEqualTo(new Address("Aktobe", 12345));
  }

  @Test
  void mapTargetPassthrough() {
    Map<String, Object> map = Map.of("k", "v");
    assertThat(converter.convert(map, Map.class)).isSameAs(map);
  }

  @Test
  void collectionTargetPassthrough() {
    List<String> list = List.of("a", "b");
    assertThat(converter.convert(list, List.class)).isSameAs(list);
    assertThat(converter.convert(list, Collection.class)).isSameAs(list);
  }

  @Test
  void unsupportedClassPairThrows() {
    assertThatThrownBy(() -> converter.convert("x", java.time.LocalDate.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot convert");
  }

  @Test
  void parameterizedListConvertsElements() {
    ParameterizedType type = parameterizedType(List.class, String.class);
    List<String> result = (List<String>) converter.convert(List.of(1, 2L, true), type);
    assertThat(result).containsExactly("1", "2", "true");
  }

  @Test
  void parameterizedMapPassthrough() {
    ParameterizedType type = parameterizedType(Map.class, String.class, Integer.class);
    Map<String, Object> map = Map.of("a", 1);
    assertThat(converter.convert(map, type)).isSameAs(map);
  }

  @Test
  void parameterizedTypeDelegatesToConvertToClass() {
    ParameterizedType type = parameterizedType(Set.class, String.class);
    List<String> list = List.of("a");
    assertThat(converter.convert(list, type)).isSameAs(list);
  }

  @Test
  void recordFromNonMapThrows() {
    assertThatThrownBy(() -> converter.convert("not a map", Person.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires a Map input");
  }

  @Test
  void arrayFromNonCollectionThrows() {
    assertThatThrownBy(() -> converter.convert("not a list", String[].class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Array target requires a Collection input");
  }

  @Test
  void unsupportedPrimitiveTypeThrows() {
    assertThatThrownBy(() -> converter.convert(1, void.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported primitive type");
  }

  private static ParameterizedType parameterizedType(Class<?> rawClass, Type... typeArgs) {
    return new ParameterizedType() {
      @Override
      public Type getRawType() {
        return rawClass;
      }

      @Override
      public Type[] getActualTypeArguments() {
        return typeArgs.clone();
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    };
  }
}

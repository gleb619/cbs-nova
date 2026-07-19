package cbs.nova.dsl.converter;

import static cbs.nova.dsl.converter.MapInputConverter.convert;
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

  record Address(String city, int zip) {
  }

  record Person(String name, Status status, Address address) {
  }

  enum Status {
    ACTIVE, INACTIVE
  }

  @Test
  void nullValueReturnsNull() {
    assertThat(convert(null, String.class)).isNull();
    assertThat(convert(null, parameterizedType(List.class, String.class))).isNull();
  }

  @Test
  void mapInputTargetPassthrough() {
    MapInput existing = MapInput.of("key", "value");
    assertThat(convert(existing, MapInput.class)).isSameAs(existing);
  }

  @Test
  void mapInputTargetWrapsMap() {
    Map<String, Object> map = Map.of("a", 1, "b", "two");
    assertThat(convert(map, MapInput.class)).isEqualTo(MapInput.fromMap(map));
  }

  @Test
  void mapInputTargetReturnsNonMapUnchanged() {
    assertThat(convert("plain", MapInput.class)).isEqualTo("plain");
    assertThat(convert(42, MapInput.class)).isEqualTo(42);
  }

  @Test
  void alreadyInstanceClassTargetPassthrough() {
    String s = "value";
    assertThat(convert(s, String.class)).isSameAs(s);

    Integer i = 7;
    assertThat(convert(i, Integer.class)).isSameAs(i);
  }

  @Test
  void stringTargetConvertsViaValueOf() {
    assertThat(convert(42, String.class)).isEqualTo("42");
    assertThat(convert(true, String.class)).isEqualTo("true");
  }

  @Test
  void booleanTargetParsesString() {
    assertThat(convert("true", boolean.class)).isEqualTo(true);
    assertThat(convert("false", Boolean.class)).isEqualTo(false);
  }

  @Test
  void byteTargetNarrowsNumber() {
    assertThat(convert(42, byte.class)).isEqualTo((byte) 42);
    assertThat(convert(42, Byte.class)).isEqualTo(Byte.valueOf((byte) 42));
  }

  @Test
  void shortTargetNarrowsNumber() {
    assertThat(convert(42, short.class)).isEqualTo((short) 42);
    assertThat(convert(42, Short.class)).isEqualTo(Short.valueOf((short) 42));
  }

  @Test
  void intTargetNarrowsNumber() {
    assertThat(convert(3L, int.class)).isEqualTo(3);
    assertThat(convert(3L, Integer.class)).isEqualTo(3);
    assertThat(convert(3.9d, int.class)).isEqualTo(3);
  }

  @Test
  void longTargetNarrowsNumber() {
    assertThat(convert(3, long.class)).isEqualTo(3L);
    assertThat(convert(3, Long.class)).isEqualTo(3L);
  }

  @Test
  void floatTargetNarrowsNumber() {
    assertThat(convert(1.5d, float.class)).isEqualTo(1.5f);
    assertThat(convert(1.5d, Float.class)).isEqualTo(1.5f);
  }

  @Test
  void doubleTargetNarrowsNumber() {
    assertThat(convert(1.5f, double.class)).isEqualTo(1.5d);
    assertThat(convert(1.5f, Double.class)).isEqualTo(1.5d);
  }

  @Test
  void charTargetTakesFirstCharacter() {
    assertThat(convert("A", char.class)).isEqualTo('A');
    assertThat(convert("Apple", Character.class)).isEqualTo('A');
  }

  @Test
  void enumTargetUsesValueOf() {
    assertThat(convert("ACTIVE", Status.class)).isEqualTo(Status.ACTIVE);
  }

  @Test
  void arrayTargetFromCollection() {
    int[] result = (int[]) convert(List.of(1, 2, 3), int[].class);
    assertThat(result).containsExactly(1, 2, 3);
  }

  @Test
  void arrayTargetConvertsElements() {
    String[] result = (String[]) convert(List.of(1, 2), String[].class);
    assertThat(result).containsExactly("1", "2");
  }

  @Test
  void recordTargetFromMapRecursively() {
    Map<String, Object> addressMap = Map.of("city", "Aktobe", "zip", 12345);
    Map<String, Object> personMap = Map.of("name", "Joe", "status", "ACTIVE", "address",
            addressMap);

    Person person = (Person) convert(personMap, Person.class);

    assertThat(person.name()).isEqualTo("Joe");
    assertThat(person.status()).isEqualTo(Status.ACTIVE);
    assertThat(person.address()).isEqualTo(new Address("Aktobe", 12345));
  }

  @Test
  void mapTargetPassthrough() {
    Map<String, Object> map = Map.of("k", "v");
    assertThat(convert(map, Map.class)).isSameAs(map);
  }

  @Test
  void collectionTargetPassthrough() {
    List<String> list = List.of("a", "b");
    assertThat(convert(list, List.class)).isSameAs(list);
    assertThat(convert(list, Collection.class)).isSameAs(list);
  }

  @Test
  void unsupportedClassPairThrows() {
    assertThatThrownBy(() -> convert("x", java.time.LocalDate.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot convert");
  }

  @Test
  void parameterizedListConvertsElements() {
    ParameterizedType type = parameterizedType(List.class, String.class);
    List<String> result = (List<String>) convert(List.of(1, 2L, true), type);
    assertThat(result).containsExactly("1", "2", "true");
  }

  @Test
  void parameterizedMapPassthrough() {
    ParameterizedType type = parameterizedType(Map.class, String.class, Integer.class);
    Map<String, Object> map = Map.of("a", 1);
    assertThat(convert(map, type)).isSameAs(map);
  }

  @Test
  void parameterizedTypeDelegatesToConvertToClass() {
    ParameterizedType type = parameterizedType(Set.class, String.class);
    List<String> list = List.of("a");
    assertThat(convert(list, type)).isSameAs(list);
  }

  @Test
  void recordFromNonMapThrows() {
    assertThatThrownBy(() -> convert("not a map", Person.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires a Map input");
  }

  @Test
  void arrayFromNonCollectionThrows() {
    assertThatThrownBy(() -> convert("not a list", String[].class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Array target requires a Collection input");
  }

  @Test
  void unsupportedPrimitiveTypeThrows() {
    assertThatThrownBy(() -> convert(1, void.class))
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

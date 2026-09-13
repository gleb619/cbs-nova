package cbs.nova.dsl.jsonschema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.ParameterType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class JsonSchemaGeneratorTest {

  private record Address(String city) {
  }

  private record Person(String name, @Nullable String nickname, int age, boolean active) {
  }

  private record NestedPerson(Address address) {
  }

  private record WithList(List<String> tags, List<Address> addresses) {
  }

  private record WithMap(Map<String, String> metadata) {
  }

  private record Empty() {
  }

  private static final String DRAFT_URI = "https://json-schema.org/draft/2020-12/schema";

  private JsonSchemaGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new JacksonJsonSchemaGenerator();
  }

  @Test
  void emptyParametersReturnsGenericObjectSchema() {
    Map<String, Object> schema = generator.generateSchema(List.of());
    assertThat(schema).containsEntry("$schema", DRAFT_URI).containsEntry("type", "object");
    assertThat(schema).doesNotContainKey("properties");
  }

  @Test
  void stringParameter() {
    Map<String, Object> schema = generator
            .generateSchema(List.of(ParameterDescriptor.ofString("name")));
    assertSchemaHeader(schema);
    assertThat(schema).hasEntrySatisfying("properties", props -> {
      Map<String, Object> properties = (Map<String, Object>) props;
      assertThat(properties).containsEntry("name", Map.of("type", "string"));
    });
    assertThat(schema.get("required")).asList().containsExactly("name");
  }

  @Test
  void numberParameter() {
    Map<String, Object> schema = generator
            .generateSchema(List.of(ParameterDescriptor.ofNumber("amount")));
    assertSchemaHeader(schema);
    assertThat(getProperties(schema)).containsEntry("amount", Map.of("type", "number"));
    assertThat(schema.get("required")).asList().containsExactly("amount");
  }

  @Test
  void booleanParameter() {
    Map<String, Object> schema = generator
            .generateSchema(List.of(ParameterDescriptor.ofBoolean("flag")));
    assertSchemaHeader(schema);
    assertThat(getProperties(schema)).containsEntry("flag", Map.of("type", "boolean"));
    assertThat(schema.get("required")).asList().containsExactly("flag");
  }

  @Test
  void objectParameterWithRecordType() {
    Map<String, Object> schema = generator
            .generateSchema(List.of(ParameterDescriptor.ofObject("person", Person.class)));
    assertSchemaHeader(schema);

    Map<String, Object> personSchema = getProperty(schema, "person");
    assertThat(personSchema.get("type")).isEqualTo("object");
    assertThat(getNestedProperties(personSchema))
            .containsEntry("name", Map.of("type", "string"))
            .containsEntry("age", Map.of("type", "number"))
            .containsEntry("active", Map.of("type", "boolean"));
    assertThat(getRequired(personSchema)).containsExactly("name", "age", "active");
  }

  @Test
  void objectParameterWithoutObjectTypeFallsBackToGenericObject() {
    Map<String, Object> schema = generator.generateSchema(
            List.of(new ParameterDescriptor("payload", ParameterType.OBJECT, null)));
    assertSchemaHeader(schema);
    assertThat(getProperties(schema)).containsEntry("payload", Map.of("type", "object"));
  }

  @Test
  void mixedParametersKeepOrderAndMarkRequired() {
    Map<String, Object> schema = generator.generateSchema(
            List.of(
                    ParameterDescriptor.ofString("first"),
                    ParameterDescriptor.ofNumber("second"),
                    ParameterDescriptor.ofBoolean("third")));
    assertThat(getRequired(schema)).containsExactly("first", "second", "third");
    assertThat(getProperties(schema).keySet()).containsExactly("first", "second", "third");
  }

  @Test
  void recordReflectionMarksNullableFieldsOptional() {
    Map<String, Object> schema = generator.generateSchema(Person.class);
    assertSchemaHeader(schema);
    assertThat(getRequired(schema))
            .containsExactly("name", "age", "active")
            .doesNotContain("nickname");
    assertThat(getProperties(schema))
            .containsEntry("name", Map.of("type", "string"))
            .containsEntry("nickname", Map.of("type", "string"))
            .containsEntry("age", Map.of("type", "number"))
            .containsEntry("active", Map.of("type", "boolean"));
  }

  @Test
  void recordReflectionHandlesNestedRecords() {
    Map<String, Object> schema = generator.generateSchema(NestedPerson.class);
    assertSchemaHeader(schema);

    Map<String, Object> addressSchema = getProperty(schema, "address");
    assertThat(addressSchema.get("type")).isEqualTo("object");
    assertThat(getNestedProperties(addressSchema)).containsEntry("city", Map.of("type", "string"));
    assertThat(getRequired(addressSchema)).containsExactly("city");
  }

  @Test
  void recordReflectionHandlesListComponents() {
    Map<String, Object> schema = generator.generateSchema(WithList.class);
    assertSchemaHeader(schema);

    Map<String, Object> tagsSchema = getProperty(schema, "tags");
    assertThat(tagsSchema.get("type")).isEqualTo("array");
    assertThat(tagsSchema.get("items")).isEqualTo(Map.of("type", "string"));

    Map<String, Object> addressesSchema = getProperty(schema, "addresses");
    assertThat(addressesSchema.get("type")).isEqualTo("array");
    assertThat(addressesSchema.get("items")).isInstanceOf(Map.class);
  }

  @Test
  void recordReflectionHandlesMapComponents() {
    Map<String, Object> schema = generator.generateSchema(WithMap.class);
    assertSchemaHeader(schema);

    Map<String, Object> metadataSchema = getProperty(schema, "metadata");
    assertThat(metadataSchema.get("type")).isEqualTo("object");
  }

  @Test
  void nonRecordClassFallsBackToGenericObject() {
    Map<String, Object> schema = generator.generateSchema(String.class);
    assertThat(schema).containsEntry("$schema", DRAFT_URI).containsEntry("type", "object");
    assertThat(schema).doesNotContainKey("properties");
  }

  @Test
  void recordWithNoComponentsFallsBackToGenericObject() {
    Map<String, Object> schema = generator.generateSchema(Empty.class);
    assertThat(schema).containsEntry("$schema", DRAFT_URI).containsEntry("type", "object");
    assertThat(schema).doesNotContainKey("properties");
  }

  // --- T423: cache + deep-immutability tests for generateSchema(Class<?>) ---

  @Test
  void sameRecordClassIsCachedAcrossCalls() {
    Map<String, Object> first = generator.generateSchema(Person.class);
    Map<String, Object> second = generator.generateSchema(Person.class);
    assertThat(first).isEqualTo(second);
    // Same instance proves the cache returned the stored entry.
    assertThat(first).isSameAs(second);
  }

  @Test
  void distinctRecordClassesCachedIndependently() {
    Map<String, Object> personSchema = generator.generateSchema(Person.class);
    Map<String, Object> addressSchema = generator.generateSchema(Address.class);

    assertThat(personSchema).containsEntry("type", "object");
    assertThat(addressSchema).containsEntry("type", "object");
    assertThat(getProperties(personSchema)).containsKey("name").doesNotContainKey("city");
    assertThat(getProperties(addressSchema)).containsKey("city").doesNotContainKey("name");

    // Both classes are also cached on a second call.
    assertThat(generator.generateSchema(Person.class)).isSameAs(personSchema);
    assertThat(generator.generateSchema(Address.class)).isSameAs(addressSchema);
  }

  @Test
  void cachedSchemaIsDeeplyUnmodifiable() {
    Map<String, Object> schema = generator.generateSchema(NestedPerson.class);

    // Top-level Map.put must throw.
    assertThrows(UnsupportedOperationException.class,
            () -> schema.put("sneaky", "value"));

    // Nested property Map must also throw.
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    @SuppressWarnings("unchecked")
    Map<String, Object> addressSchema = (Map<String, Object>) properties.get("address");
    assertThrows(UnsupportedOperationException.class,
            () -> addressSchema.put("sneaky", "value"));

    // Nested required List must also throw.
    @SuppressWarnings("unchecked")
    List<String> addressRequired = (List<String>) addressSchema.get("required");
    assertThrows(UnsupportedOperationException.class,
            () -> addressRequired.add("sneaky"));

    // A subsequent call must still return the same correct (and unmodifiable) schema.
    Map<String, Object> second = generator.generateSchema(NestedPerson.class);
    assertThat(second).isSameAs(schema);
    assertThat(getProperties(second)).containsKey("address");
  }

  @Test
  void arrayItemsAreDeeplyUnmodifiable() {
    Map<String, Object> schema = generator.generateSchema(WithList.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    @SuppressWarnings("unchecked")
    Map<String, Object> addressesSchema = (Map<String, Object>) properties.get("addresses");
    // addresses.items is a Map (nested record elements) — must be unmodifiable.
    Object items = addressesSchema.get("items");
    assertThat(items).isInstanceOf(Map.class);
    assertThrows(UnsupportedOperationException.class,
            () -> ((Map<String, Object>) items).put("sneaky", "value"));
  }

  @Test
  void nullAndNonRecordReturnFreshEmptyObjectSchemaEachCall() {
    Map<String, Object> nullSchema1 = generator.generateSchema((Class<?>) null);
    Map<String, Object> nullSchema2 = generator.generateSchema((Class<?>) null);
    assertThat(nullSchema1).containsEntry("$schema", DRAFT_URI).containsEntry("type", "object");
    // Each call must produce a fresh, mutable map (not cached, not shared).
    assertThat(nullSchema1).isNotSameAs(nullSchema2);
    assertThat(nullSchema1).isEqualTo(nullSchema2);

    Map<String, Object> stringSchema = generator.generateSchema(String.class);
    assertThat(stringSchema).containsEntry("type", "object");
    assertThat(stringSchema).doesNotContainKey("properties");
  }

  @Test
  void computePathInvokedOncePerDistinctRecordClass() {
    // Two distinct classes must each trigger exactly one compute.
    CountingGenerator counting = new CountingGenerator();
    counting.generateSchema(Person.class);
    counting.generateSchema(Person.class);
    counting.generateSchema(Person.class);
    counting.generateSchema(Address.class);
    counting.generateSchema(Address.class);

    assertThat(counting.computeCount(Person.class)).isEqualTo(1);
    assertThat(counting.computeCount(Address.class)).isEqualTo(1);
  }

  /** Test subclass that records how often {@code computeSchema} is invoked per Class. */
  private static final class CountingGenerator extends JacksonJsonSchemaGenerator {
    private final Map<Class<?>, Integer> counts = new HashMap<>();

    int computeCount(Class<?> type) {
      return counts.getOrDefault(type, 0);
    }

    @Override
    protected Map<String, Object> computeSchema(Class<?> inputType) {
      counts.merge(inputType, 1, Integer::sum);
      return super.computeSchema(inputType);
    }
  }

  private static void assertSchemaHeader(Map<String, Object> schema) {
    assertThat(schema).containsEntry("$schema", DRAFT_URI).containsEntry("type", "object");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> getProperties(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> getNestedProperties(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
  }

  @SuppressWarnings("unchecked")
  private static List<String> getRequired(Map<String, Object> schema) {
    return (List<String>) schema.get("required");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> getProperty(Map<String, Object> schema, String key) {
    return (Map<String, Object>) getProperties(schema).get(key);
  }
}

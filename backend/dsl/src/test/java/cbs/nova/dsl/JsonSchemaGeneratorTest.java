package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.jsonschema.VictoolsJsonSchemaGenerator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    generator = new VictoolsJsonSchemaGenerator();
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

package cbs.nova.starter.validation;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.model.ValidationError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatorTest {

  @Test
  void validObjectReturnsEmptyErrors() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("name", Map.of("type", "string")),
            "required", List.of("name"));
    Map<String, Object> body = Map.of("name", "Alice");

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void validArrayReturnsEmptyErrors() {
    Map<String, Object> schema = Map.of(
            "type", "array",
            "items", Map.of("type", "string"));
    List<Object> body = List.of("a", "b");

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void validStringReturnsEmptyErrors() {
    Map<String, Object> schema = Map.of("type", "string");

    List<ValidationError> errors = JsonSchemaValidator.validate("hello", schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void validNumberReturnsEmptyErrors() {
    Map<String, Object> schema = Map.of("type", "number");

    List<ValidationError> errors = JsonSchemaValidator.validate(42, schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void validBooleanReturnsEmptyErrors() {
    Map<String, Object> schema = Map.of("type", "boolean");

    List<ValidationError> errors = JsonSchemaValidator.validate(true, schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void validNullReturnsEmptyErrors() {
    Map<String, Object> schema = Map.of("type", "null");

    List<ValidationError> errors = JsonSchemaValidator.validate(null, schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void validAnyReturnsEmptyErrors() {
    Map<String, Object> schema = Map.of("type", "any");

    List<ValidationError> errors = JsonSchemaValidator.validate("anything", schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void typeMismatchStringReturnsError() {
    Map<String, Object> schema = Map.of("type", "string");

    List<ValidationError> errors = JsonSchemaValidator.validate(42, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$");
    assertThat(errors.get(0).message()).isEqualTo("expected type string");
    assertThat(errors.get(0).severity()).isEqualTo("error");
  }

  @Test
  void typeMismatchNumberReturnsError() {
    Map<String, Object> schema = Map.of("type", "number");

    List<ValidationError> errors = JsonSchemaValidator.validate("not a number", schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$");
    assertThat(errors.get(0).message()).isEqualTo("expected type number");
  }

  @Test
  void typeMismatchBooleanReturnsError() {
    Map<String, Object> schema = Map.of("type", "boolean");

    List<ValidationError> errors = JsonSchemaValidator.validate("yes", schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$");
    assertThat(errors.get(0).message()).isEqualTo("expected type boolean");
  }

  @Test
  void typeMismatchNullReturnsError() {
    Map<String, Object> schema = Map.of("type", "null");

    List<ValidationError> errors = JsonSchemaValidator.validate("not null", schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$");
    assertThat(errors.get(0).message()).isEqualTo("expected type null");
  }

  @Test
  void typeMismatchObjectReturnsError() {
    Map<String, Object> schema = Map.of("type", "object");

    List<ValidationError> errors = JsonSchemaValidator.validate("string", schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$");
    assertThat(errors.get(0).message()).isEqualTo("expected type object");
  }

  @Test
  void typeMismatchArrayReturnsError() {
    Map<String, Object> schema = Map.of("type", "array");

    List<ValidationError> errors = JsonSchemaValidator.validate("string", schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$");
    assertThat(errors.get(0).message()).isEqualTo("expected type array");
  }

  @Test
  void numberRejectsBooleanBecauseOfExclusion() {
    Map<String, Object> schema = Map.of("type", "number");

    List<ValidationError> errors = JsonSchemaValidator.validate(true, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).message()).isEqualTo("expected type number");
  }

  @Test
  void missingRequiredPropertyReturnsError() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "required", List.of("name"));
    Map<String, Object> body = Map.of();

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.name");
    assertThat(errors.get(0).message()).isEqualTo("field is required");
  }

  @Test
  void missingMultipleRequiredPropertiesReturnsMultipleErrors() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "required", List.of("name", "age"));
    Map<String, Object> body = Map.of();

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(2);
    assertThat(errors).extracting(ValidationError::field).containsExactlyInAnyOrder(
            "$.name", "$.age");
  }

  @Test
  void requiredPropertyWithNullValueReturnsError() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "required", List.of("name"));
    Map<String, Object> body = new HashMap<>();
    body.put("name", null);

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.name");
    assertThat(errors.get(0).message()).isEqualTo("field is required");
  }

  @Test
  void arrayItemsViolationReturnsErrorWithPointerPath() {
    Map<String, Object> schema = Map.of(
            "type", "array",
            "items", Map.of("type", "string"));
    List<Object> body = List.of("ok", 42);

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.items[1]");
    assertThat(errors.get(0).message()).isEqualTo("expected type string");
  }

  @Test
  void arrayItemsMultipleViolations() {
    Map<String, Object> schema = Map.of(
            "type", "array",
            "items", Map.of("type", "number"));
    List<Object> body = List.of("a", "b");

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(2);
    assertThat(errors).extracting(ValidationError::field)
            .containsExactlyInAnyOrder("$.items[0]", "$.items[1]");
  }

  @Test
  void arrayItemsNestedObjectViolation() {
    Map<String, Object> itemsSchema = Map.of(
            "type", "object",
            "properties", Map.of("id", Map.of("type", "number")));
    Map<String, Object> schema = Map.of(
            "type", "array",
            "items", itemsSchema);
    List<Object> body = List.of(Map.of("id", 1), Map.of("id", "bad"));

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.items[1].id");
    assertThat(errors.get(0).message()).isEqualTo("expected type number");
  }

  @Test
  void nullTypeValueInSchemaIsPermissive() {
    Map<String, Object> schema = new HashMap<>();
    schema.put("type", null);

    List<ValidationError> errors = JsonSchemaValidator.validate("anything", schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void absentTypeIsPermissive() {
    Map<String, Object> schema = Map.of("properties", Map.of("x", Map.of("type", "string")));

    List<ValidationError> errors = JsonSchemaValidator.validate(42, schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void typeAnyIsPermissive() {
    Map<String, Object> schema = Map.of("type", "any");

    List<ValidationError> errors = JsonSchemaValidator.validate(Map.of("deep", "value"), schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void nestedObjectDeepPointerPath() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                    "user", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "address", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "zip", Map.of("type", "string")))))));
    Map<String, Object> body = Map.of(
            "user", Map.of(
                    "address", Map.of("zip", 12345)));

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.user.address.zip");
    assertThat(errors.get(0).message()).isEqualTo("expected type string");
  }

  @Test
  void nonStringTypeInSchemaTreatedAsAbsent() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("field", 42));
    Map<String, Object> body = Map.of("field", "value");

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).isEmpty();
  }

  @Test
  void missingPropertyWithNestedSchemaProducesCorrectPointer() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                    "nested", Map.of(
                            "type", "object",
                            "required", List.of("id"))));
    Map<String, Object> body = Map.of("nested", Map.of());

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.nested.id");
    assertThat(errors.get(0).message()).isEqualTo("field is required");
  }

  @Test
  void objectPropertyTypeMismatchReturnsCorrectPointer() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("count", Map.of("type", "number")));
    Map<String, Object> body = Map.of("count", "not a number");

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.count");
    assertThat(errors.get(0).message()).isEqualTo("expected type number");
  }

  @Test
  void emptyObjectBodyWithRequiredReturnsMultipleErrors() {
    Map<String, Object> schema = Map.of(
            "type", "object",
            "required", List.of("a", "b", "c"));
    Map<String, Object> body = Map.of();

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).hasSize(3);
    assertThat(errors).extracting(ValidationError::field)
            .containsExactlyInAnyOrder("$.a", "$.b", "$.c");
  }

  @Test
  void arrayWithNoItemsSchemaSkipsValidation() {
    Map<String, Object> schema = Map.of("type", "array");
    List<Object> body = List.of(1, "mixed", true);

    List<ValidationError> errors = JsonSchemaValidator.validate(body, schema);

    assertThat(errors).isEmpty();
  }
}

package cbs.nova.starter.validation;

import cbs.nova.starter.model.ValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates a deserialized JSON body against the subset of JSON Schema produced by
 * {@code JacksonJsonSchemaGenerator}: object/array/string/number/boolean/null/any types,
 * {@code properties}, {@code required}, and {@code items}.
 *
 * <p>
 * Field pointers follow a JSONPath-like convention: {@code $} for the root, {@code $.field} for
 * object properties, and {@code $.items[0].field} for array elements.
 */
public final class JsonSchemaValidator {

  private static final String ROOT_PATH = "$";

  private JsonSchemaValidator() {
  }

  public static List<ValidationError> validate(Object body, Map<String, Object> schema) {
    return validateValue(body, schema, ROOT_PATH);
  }

  private static List<ValidationError> validateValue(Object value, Map<String, Object> schema,
          String path) {
    List<ValidationError> errors = new ArrayList<>();
    String type = schema.get("type") instanceof String s ? s : null;

    if (type == null || "any".equals(type)) {
      return errors;
    }

    switch (type) {
      case "object" -> validateObject(value, schema, path, errors);
      case "array" -> validateArray(value, schema, path, errors);
      case "string" -> requireType(value instanceof CharSequence, path,
              "expected type string", errors);
      case "number" -> requireType(value instanceof Number && !(value instanceof Boolean),
              path, "expected type number", errors);
      case "boolean" -> requireType(value instanceof Boolean, path,
              "expected type boolean", errors);
      case "null" -> requireType(value == null, path, "expected type null", errors);
      default -> {
        // Unknown type: no validation.
      }
    }

    return errors;
  }

  private static void requireType(boolean condition, String path, String message,
          List<ValidationError> errors) {
    if (!condition) {
      errors.add(new ValidationError(path, message, "error"));
    }
  }

  @SuppressWarnings("unchecked")
  private static void validateObject(Object value, Map<String, Object> schema, String path,
          List<ValidationError> errors) {
    if (!(value instanceof Map<?, ?> map)) {
      errors.add(new ValidationError(path, "expected type object", "error"));
      return;
    }

    List<String> required = schema.get("required") instanceof List<?> list
            ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
            : List.of();
    for (String name : required) {
      if (!map.containsKey(name) || map.get(name) == null) {
        errors.add(new ValidationError(path + "." + name, "field is required", "error"));
      }
    }

    Map<String, Object> properties = schema.get("properties") instanceof Map<?, ?> raw
            ? (Map<String, Object>) raw
            : Map.of();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      String name = entry.getKey();
      if (entry.getValue() instanceof Map<?, ?> propertySchema
              && map.containsKey(name)
              && map.get(name) != null) {
        errors.addAll(validateValue(map.get(name), (Map<String, Object>) propertySchema,
                path + "." + name));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void validateArray(Object value, Map<String, Object> schema, String path,
          List<ValidationError> errors) {
    if (!(value instanceof List<?> list)) {
      errors.add(new ValidationError(path, "expected type array", "error"));
      return;
    }

    if (!(schema.get("items") instanceof Map<?, ?> rawItems)) {
      return;
    }
    Map<String, Object> items = (Map<String, Object>) rawItems;
    for (int i = 0; i < list.size(); i++) {
      errors.addAll(validateValue(list.get(i), items, path + ".items[" + i + "]"));
    }
  }
}

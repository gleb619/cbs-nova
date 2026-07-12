package cbs.nova.dsl;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a JSON Schema (Draft 2020-12) from a DSL input shape.
 *
 * <p>
 * The schema is returned as a plain {@link Map} so it can be serialized to JSON by any mapper.
 */
public final class JsonSchemaGenerator {

  private static final String DRAFT_URI = "https://json-schema.org/draft/2020-12/schema";

  private JsonSchemaGenerator() {
  }

  /**
   * Builds a schema from an explicit parameter list.
   *
   * <p>
   * All declared parameters are marked as required because the DSL registry does not carry optional
   * flags.
   */
  public static Map<String, Object> generateSchema(@Nullable List<ParameterDescriptor> parameters) {
    Map<String, Object> schema = emptyObjectSchema();
    if (parameters == null || parameters.isEmpty()) {
      return schema;
    }

    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (ParameterDescriptor descriptor : parameters) {
      properties.put(descriptor.name(), parameterSchema(descriptor));
      required.add(descriptor.name());
    }
    schema.put("properties", properties);
    schema.put("required", required);
    return schema;
  }

  /**
   * Builds a schema from an input type record by reflecting over its components.
   *
   * <p>
   * Components annotated with any {@code Nullable} annotation are omitted from the required list.
   * If reflection fails or the record has no components, a generic {@code {"type":"object"}} schema
   * is returned.
   */
  public static Map<String, Object> generateSchema(@Nullable Class<?> inputType) {
    if (inputType == null || !inputType.isRecord()) {
      return emptyObjectSchema();
    }

    RecordComponent[] components;
    try {
      components = inputType.getRecordComponents();
    } catch (Throwable t) {
      return emptyObjectSchema();
    }
    if (components == null || components.length == 0) {
      return emptyObjectSchema();
    }

    Map<String, Object> schema = emptyObjectSchema();
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (RecordComponent component : components) {
      properties.put(component.getName(),
              schemaForJavaType(component.getType(), component.getGenericType()));
      if (!isNullable(component)) {
        required.add(component.getName());
      }
    }
    schema.put("properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }
    return schema;
  }

  private static Map<String, Object> emptyObjectSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("$schema", DRAFT_URI);
    schema.put("type", "object");
    return schema;
  }

  private static Map<String, Object> parameterSchema(ParameterDescriptor descriptor) {
    return switch (descriptor.type()) {
      case STRING -> Map.of("type", "string");
      case NUMBER -> Map.of("type", "number");
      case BOOLEAN -> Map.of("type", "boolean");
      case OBJECT -> objectParameterSchema(descriptor.objectType());
      default -> descriptor.type().name().equals("LIST")
              ? listParameterSchema(descriptor.objectType())
              : Map.of("type", "object");
    };
  }

  private static Map<String, Object> objectParameterSchema(@Nullable Class<?> type) {
    if (type != null && type.isRecord()) {
      return generateSchema(type);
    }
    return Map.of("type", "object");
  }

  private static Map<String, Object> listParameterSchema(@Nullable Class<?> itemType) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "array");
    schema.put("items", objectParameterSchema(itemType));
    return schema;
  }

  private static Map<String, Object> schemaForJavaType(Class<?> rawType, Type genericType) {
    if (rawType == boolean.class || rawType == Boolean.class) {
      return Map.of("type", "boolean");
    }
    if (rawType == String.class || CharSequence.class.isAssignableFrom(rawType)) {
      return Map.of("type", "string");
    }
    if (isNumber(rawType)) {
      return Map.of("type", "number");
    }
    if (rawType.isRecord()) {
      return generateSchema(rawType);
    }
    if (rawType.isArray() || Collection.class.isAssignableFrom(rawType)) {
      return arraySchema(rawType, genericType);
    }
    if (Map.class.isAssignableFrom(rawType)) {
      return Map.of("type", "object");
    }
    return Map.of("type", "object");
  }

  private static boolean isNumber(Class<?> type) {
    return Number.class.isAssignableFrom(type)
            || type == byte.class
            || type == short.class
            || type == int.class
            || type == long.class
            || type == float.class
            || type == double.class;
  }

  private static Map<String, Object> arraySchema(Class<?> rawType, Type genericType) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "array");

    Class<?> itemType = Object.class;
    if (rawType.isArray()) {
      itemType = rawType.getComponentType();
    } else if (genericType instanceof ParameterizedType pt) {
      Type[] args = pt.getActualTypeArguments();
      if (args.length == 1 && args[0] instanceof Class<?> c) {
        itemType = c;
      }
    }
    schema.put("items", schemaForJavaType(itemType, itemType));
    return schema;
  }

  private static boolean isNullable(RecordComponent component) {
    return hasNullableAnnotation(component.getAnnotatedType().getAnnotations())
            || hasNullableAnnotation(
                    component.getAccessor().getAnnotatedReturnType().getAnnotations())
            || hasNullableAnnotation(component.getAnnotations())
            || hasNullableAnnotation(component.getAccessor().getAnnotations());
  }

  private static boolean hasNullableAnnotation(java.lang.annotation.Annotation[] annotations) {
    return Arrays.stream(annotations)
            .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
  }
}

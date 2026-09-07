package cbs.nova.dsl.jsonschema;

import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.ParameterDescriptor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsonFormatVisitors.JsonAnyFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds JSON schemas from Jackson 3 serialization metadata via
 * {@link ObjectMapper#acceptJsonFormatVisitor}, without inspecting Java fields by hand.
 *
 * <p>
 * Property order follows the order Jackson's serializers emit properties in (record component
 * declaration order), and required-ness comes from {@link NullableRecordAnnotationIntrospector}
 * which routes nullable record components to {@code optionalProperty(...)}.
 * </p>
 */
public class JacksonJsonSchemaGenerator implements JsonSchemaGenerator {

  private static final String DRAFT_URI = "https://json-schema.org/draft/2020-12/schema";

  private final ObjectMapper objectMapper = JsonMapper.builder()
          .annotationIntrospector(new NullableRecordAnnotationIntrospector())
          .build();

  @Override
  public Map<String, Object> generateSchema(@Nullable List<ParameterDescriptor> parameters) {
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

  @Override
  public Map<String, Object> generateSchema(@Nullable Class<?> inputType) {
    if (inputType == null || !inputType.isRecord()) {
      return emptyObjectSchema();
    }

    SchemaBuildingVisitor visitor = new SchemaBuildingVisitor();
    objectMapper.acceptJsonFormatVisitor(inputType, visitor);
    Map<String, Object> generated = visitor.schema();
    if (generated == null) {
      return emptyObjectSchema();
    }

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("$schema", DRAFT_URI);
    schema.putAll(generated);
    if (generated.get("properties") instanceof Map<?, ?> properties && properties.isEmpty()) {
      schema.remove("properties");
      schema.remove("required");
    }
    return schema;
  }

  private Map<String, Object> emptyObjectSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("$schema", DRAFT_URI);
    schema.put("type", "object");
    return schema;
  }

  private Map<String, Object> parameterSchema(ParameterDescriptor descriptor) {
    return switch (descriptor.type()) {
      case STRING -> Map.of("type", "string");
      case NUMBER -> Map.of("type", "number");
      case BOOLEAN -> Map.of("type", "boolean");
      case OBJECT -> objectParameterSchema(descriptor.objectType());
    };
  }

  private Map<String, Object> objectParameterSchema(@Nullable Class<?> type) {
    if (type != null && type.isRecord()) {
      return generateSchema(type);
    }
    return Map.of("type", "object");
  }

  private Map<String, Object> schemaFor(JavaType type) {
    SchemaBuildingVisitor child = new SchemaBuildingVisitor();
    objectMapper.acceptJsonFormatVisitor(type, child);
    Map<String, Object> schema = child.schema();
    return schema != null ? schema : new LinkedHashMap<>();
  }

  private final class SchemaBuildingVisitor implements JsonFormatVisitorWrapper {

    private @Nullable Map<String, Object> schema;
    private @Nullable SerializationContext context;

    Map<String, Object> schema() {
      return schema;
    }

    @Override
    public void setContext(SerializationContext context) {
      this.context = context;
    }

    @Override
    public @Nullable SerializationContext getContext() {
      return context;
    }

    @Override
    public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
      Map<String, Object> properties = new LinkedHashMap<>();
      List<String> required = new ArrayList<>();
      Map<String, Object> objectSchema = new LinkedHashMap<>();
      objectSchema.put("type", "object");
      objectSchema.put("properties", properties);
      objectSchema.put("required", required);
      schema = objectSchema;
      return new JsonObjectFormatVisitor() {
        @Override
        public void property(BeanProperty prop) {
          addProperty(properties, required, prop.getName(), prop.getType(), true);
        }

        @Override
        public void optionalProperty(BeanProperty prop) {
          addProperty(properties, required, prop.getName(), prop.getType(), false);
        }

        @Override
        public void property(String name, JsonFormatVisitable propertyType, JavaType typeHint) {
          addProperty(properties, required, name, typeHint, true);
        }

        @Override
        public void optionalProperty(String name, JsonFormatVisitable propertyType,
                JavaType typeHint) {
          addProperty(properties, required, name, typeHint, false);
        }

        @Override
        public SerializationContext getContext() {
          return SchemaBuildingVisitor.this.context;
        }
      };
    }

    @Override
    public JsonArrayFormatVisitor expectArrayFormat(JavaType type) {
      Map<String, Object> arraySchema = new LinkedHashMap<>();
      arraySchema.put("type", "array");
      schema = arraySchema;
      return new JsonArrayFormatVisitor() {
        @Override
        public void itemsFormat(JsonFormatVisitable elementFormat, JavaType contentType) {
          arraySchema.put("items", schemaFor(contentType));
        }

        @Override
        public void itemsFormat(JsonFormatTypes format) {
          arraySchema.put("items", Map.of("type", format.value()));
        }

        @Override
        public SerializationContext getContext() {
          return SchemaBuildingVisitor.this.context;
        }
      };
    }

    @Override
    public JsonMapFormatVisitor expectMapFormat(JavaType type) {
      // Map types surface as plain object schemas without fixed properties.
      schema = new LinkedHashMap<>(Map.of("type", "object"));
      return new JsonMapFormatVisitor() {
        @Override
        public void keyFormat(JsonFormatVisitable keyFormat, JavaType keyType) {
        }

        @Override
        public void valueFormat(JsonFormatVisitable valueFormat, JavaType valueType) {
        }

        @Override
        public SerializationContext getContext() {
          return SchemaBuildingVisitor.this.context;
        }
      };
    }

    @Override
    public JsonStringFormatVisitor expectStringFormat(JavaType type) {
      schema = new LinkedHashMap<>(Map.of("type", "string"));
      return null;
    }

    @Override
    public JsonNumberFormatVisitor expectNumberFormat(JavaType type) {
      schema = new LinkedHashMap<>(Map.of("type", "number"));
      return null;
    }

    @Override
    public JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) {
      // Contract maps every numeric shape, including integers, to "number".
      schema = new LinkedHashMap<>(Map.of("type", "number"));
      return null;
    }

    @Override
    public JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) {
      schema = new LinkedHashMap<>(Map.of("type", "boolean"));
      return null;
    }

    @Override
    public JsonNullFormatVisitor expectNullFormat(JavaType type) {
      schema = new LinkedHashMap<>(Map.of("type", "null"));
      return null;
    }

    @Override
    public JsonAnyFormatVisitor expectAnyFormat(JavaType type) {
      schema = new LinkedHashMap<>(Map.of("type", "any"));
      return null;
    }

    private void addProperty(Map<String, Object> properties, List<String> required,
            String name, JavaType type, boolean isRequired) {
      properties.put(name, schemaFor(type));
      if (isRequired) {
        required.add(name);
      }
    }
  }
}

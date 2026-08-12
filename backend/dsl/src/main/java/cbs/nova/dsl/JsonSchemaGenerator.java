package cbs.nova.dsl;

import java.lang.annotation.Annotation;
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
/*
To generate a JSON schema from a Java class using Jackson, you can choose between two primary methods depending on your target JSON Schema draft version.The FasterXML Jackson Module natively supports older Draft 3 schemas. For modern standards like Draft 2020-12, the industry standard is to use the victools JSON Schema Generator alongside its Jackson integration module.Option 1: Modern Drafts via Victools Generator (Recommended)This approach supports modern JSON Schema drafts (such as Draft 2020-12) and honors Jackson serialization configurations and annotations.1. Add DependenciesAdd these dependencies to your pom.xml:xml<dependency>
    <groupId>com.github.victools</groupId>
    <artifactId>jsonschema-generator</artifactId>
    <version>4.31.1</version>
</dependency>
<dependency>
    <groupId>com.github.victools</groupId>
    <artifactId>jsonschema-module-jackson</artifactId>
    <version>4.31.1</version>
</dependency>
Use code with caution.2. Java Code Implementationjavaimport com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

public class SchemaGeneratorExample {
    public static void main(String[] args) {
        // 1. Configure the builder to use modern Draft 2020-12
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON
        );

        // 2. Register the Jackson module to respect your Jackson annotations
        configBuilder.with(new JacksonModule());

        // 3. Build configuration and generate schema
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);

        JsonNode jsonSchema = generator.generateSchema(Product.class);

        // 4. Output the beautiful, valid JSON schema string
        System.out.println(jsonSchema.toPrettyString());
    }
}
Use code with caution.Option 2: Native Jackson Module (Legacy Draft 3)If your ecosystem specifically requires older Draft 3 definitions, use the legacy module maintained by FasterXML.1. Add Dependencyxml<dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-jsonSchema</artifactId>
    <version>2.15.2</version>
</dependency>
Use code with caution.2. Java Code Implementationjavaimport com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;

public class LegacySchemaExample {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Instantiate the official native generator wrapper
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
        JsonSchema schema = schemaGen.generateSchema(Product.class);

        // Print out your JSON string payload representation
        String schemaText = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        System.out.println(schemaText);
    }
}
Use code with caution.Target POJO Class StructureBoth setups will parse a standard Java object configured with Jackson annotations:javaimport com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class Product {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The unique identifier for the product")
    private int id;

    @JsonProperty("product_name")
    private String name;

    // Getters and Setters...
}
*/
//TODO: use library instead of handwritten schemas
@Deprecated
//TODO: instead of static access, create a some bean
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

  private static boolean hasNullableAnnotation(Annotation[] annotations) {
    return Arrays.stream(annotations)
            .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
  }
}

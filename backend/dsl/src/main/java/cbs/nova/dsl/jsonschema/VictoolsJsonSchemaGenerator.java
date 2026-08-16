package cbs.nova.dsl.jsonschema;

import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.ParameterDescriptor;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.CustomDefinitionProviderV2;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VictoolsJsonSchemaGenerator implements JsonSchemaGenerator {

  private static final String DRAFT_URI = "https://json-schema.org/draft/2020-12/schema";
  private static final Set<Class<?>> NUMBER_TYPES = Set.of(
          byte.class, Byte.class,
          short.class, Short.class,
          int.class, Integer.class,
          long.class, Long.class,
          float.class, Float.class,
          double.class, Double.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SchemaGenerator schemaGenerator;

  public VictoolsJsonSchemaGenerator() {
    SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
            .with(new JacksonModule())
            .with(new NumericTypeDefinitionProvider())
            .with(new MapAsObjectDefinitionProvider());
    configBuilder.forFields()
            .withRequiredCheck(fieldScope -> !isNullable(fieldScope));
    configBuilder.forTypesInGeneral()
            .withPropertySorter(new DeclarationOrderSorter());
    SchemaGeneratorConfig config = configBuilder.build();
    this.schemaGenerator = new SchemaGenerator(config);
  }

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

    JsonNode jsonNode = schemaGenerator.generateSchema(inputType);
    return convertToMap(jsonNode);
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

  private Map<String, Object> convertToMap(JsonNode jsonNode) {
    return objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
    });
  }

  private static boolean isNullable(MemberScope<?, ?> fieldScope) {
    Object raw = fieldScope.getRawMember();
    if (raw instanceof Field field) {
      return field.getAnnotatedType().isAnnotationPresent(Nullable.class);
    }
    return false;
  }

  private static final class DeclarationOrderSorter implements Comparator<MemberScope<?, ?>> {

    @Override
    public int compare(MemberScope<?, ?> m1, MemberScope<?, ?> m2) {
      return Integer.compare(indexOf(m1), indexOf(m2));
    }

    private static int indexOf(MemberScope<?, ?> member) {
      Object raw = member.getRawMember();
      if (raw instanceof Field field) {
        Field[] declaredFields = field.getDeclaringClass().getDeclaredFields();
        for (int i = 0; i < declaredFields.length; i++) {
          if (declaredFields[i].equals(field)) {
            return i;
          }
        }
      }
      return member.getSchemaPropertyName().hashCode();
    }
  }

  private final class NumericTypeDefinitionProvider implements CustomDefinitionProviderV2 {

    @Override
    public CustomDefinition provideCustomSchemaDefinition(ResolvedType type,
            SchemaGenerationContext context) {
      if (NUMBER_TYPES.contains(type.getErasedType())) {
        ObjectNode value = objectMapper.valueToTree(Map.of("type", "number"));
        return new CustomDefinition(value, CustomDefinition.DefinitionType.INLINE,
                CustomDefinition.AttributeInclusion.YES);
      }
      return null;
    }
  }

  private final class MapAsObjectDefinitionProvider implements CustomDefinitionProviderV2 {

    @Override
    public CustomDefinition provideCustomSchemaDefinition(ResolvedType type,
            SchemaGenerationContext context) {
      if (Map.class.isAssignableFrom(type.getErasedType())) {
        ObjectNode value = objectMapper.valueToTree(Map.of("type", "object"));
        return new CustomDefinition(value, CustomDefinition.DefinitionType.INLINE,
                CustomDefinition.AttributeInclusion.YES);
      }
      return null;
    }
  }
}

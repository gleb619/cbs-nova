package cbs.nova.dsl.jsonschema;

import cbs.nova.dsl.ParameterDescriptor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface JsonSchemaGenerator {

  Map<String, Object> generateSchema(@Nullable List<ParameterDescriptor> parameters);

  Map<String, Object> generateSchema(@Nullable Class<?> inputType);
}

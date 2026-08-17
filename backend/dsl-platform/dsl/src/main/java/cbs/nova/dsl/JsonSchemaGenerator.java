package cbs.nova.dsl;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface JsonSchemaGenerator {

  Map<String, Object> generateSchema(@Nullable List<ParameterDescriptor> parameters);

  Map<String, Object> generateSchema(@Nullable Class<?> inputType);
}

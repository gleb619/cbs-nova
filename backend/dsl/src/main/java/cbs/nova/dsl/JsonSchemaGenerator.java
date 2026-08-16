package cbs.nova.dsl;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface JsonSchemaGenerator {

  /**
   * Builds a schema from an explicit parameter list.
   *
   * <p>
   * All declared parameters are marked as required because the DSL registry does not carry optional
   * flags.
   */
  Map<String, Object> generateSchema(@Nullable List<ParameterDescriptor> parameters);

  Map<String, Object> generateSchema(@Nullable Class<?> inputType);
}

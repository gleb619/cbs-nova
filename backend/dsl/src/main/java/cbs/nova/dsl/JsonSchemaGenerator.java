package cbs.nova.dsl;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Generates a JSON Schema (Draft 2020-12) from a DSL input shape.
 *
 * <p>
 * The schema is returned as a plain {@link Map} so it can be serialized to JSON by any mapper.
 */
public interface JsonSchemaGenerator {

  /**
   * Builds a schema from an explicit parameter list.
   *
   * <p>
   * All declared parameters are marked as required because the DSL registry does not carry optional
   * flags.
   */
  Map<String, Object> generateSchema(@Nullable List<ParameterDescriptor> parameters);

  /**
   * Builds a schema from an input type record by reflecting over its components.
   *
   * <p>
   * Components annotated with any {@code Nullable} annotation are omitted from the required list.
   * If reflection fails or the record has no components, a generic {@code {"type":"object"}} schema
   * is returned.
   */
  Map<String, Object> generateSchema(@Nullable Class<?> inputType);
}

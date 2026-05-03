package cbs.dsl.api;

import io.avaje.jsonb.Jsonb;

import java.util.Map;

/**
 * Utility for converting typed DSL payload records to/from {@code Map<String, Object>} using avaje
 * jsonb.
 */
public final class JsonPayload {

  private static final Jsonb JSONB = Jsonb.builder().build();

  private JsonPayload() {}

  /**
   * Converts a typed record to a {@code Map<String, Object>} by round-tripping through JSON.
   *
   * @param record the typed payload record
   * @return the map representation
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> toMap(Object record) {
    String json = JSONB.toJson(record);
    return JSONB.type(Map.class).fromJson(json);
  }

  /**
   * Converts a {@code Map<String, Object>} to a typed record by round-tripping through JSON.
   *
   * @param map the map representation
   * @param type the target record class
   * @param <T> the target type
   * @return the typed record
   */
  public static <T> T fromMap(Map<String, Object> map, Class<T> type) {
    String json = JSONB.toJson(map);
    return JSONB.type(type).fromJson(json);
  }
}

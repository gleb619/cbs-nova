package cbs.nova.dsl.converter;

import io.avaje.jsonb.Jsonb;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public final class AvajeMapConverter {

  private final Jsonb jsonb;

  public static AvajeMapConverter create() {
    return new AvajeMapConverter(Jsonb.builder().build());
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> toMap(Object record) {
    String json = jsonb.toJson(record);
    return jsonb.type(Map.class).fromJson(json);
  }

  public <T> T fromMap(Map<String, Object> map, Class<T> type) {
    String json = jsonb.toJson(map);
    return jsonb.type(type).fromJson(json);
  }
}

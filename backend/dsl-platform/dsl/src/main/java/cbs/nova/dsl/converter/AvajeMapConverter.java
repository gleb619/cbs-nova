package cbs.nova.dsl.converter;

import cbs.nova.dsl.registry.ModelRegistry;
import cbs.nova.dsl.registry.DefaultModelRegistry;
import io.avaje.jsonb.Jsonb;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@RequiredArgsConstructor
public final class AvajeMapConverter {

  private final Jsonb jsonb;
  private final ModelRegistry modelRegistry;

  public static AvajeMapConverter create() {
    return new AvajeMapConverter(Jsonb.builder().build(), new DefaultModelRegistry());
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> toMap(Object record) {
    if (record instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    @SuppressWarnings("rawtypes")
    Class recordClass = record.getClass();
    String json = jsonb.type(recordClass).toJson(record);
    return jsonb.type(Map.class).fromJson(json);
  }

  public <T> T fromMap(Map<String, Object> map, Class<T> type) {
    if (modelRegistry.isRegistered(type)) {
      return jsonb.type(type).fromObject(map);
    }
    String json = jsonb.toJson(map);
    return jsonb.type(type).fromJson(json);
  }

  public boolean supports(@NonNull Class<?> type) {
    return modelRegistry.isRegistered(type);
  }
}

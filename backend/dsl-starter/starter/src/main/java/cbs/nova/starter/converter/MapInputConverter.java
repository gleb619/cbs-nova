package cbs.nova.starter.converter;

import cbs.nova.dsl.model.MapInput;
import io.avaje.jsonb.Json;
import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class MapInputConverter {

  private final Jsonb jsonb;
  private final ObjectMapper objectMapper;

  // TODO: redo to a Caffeine with some properties config for ttl
  private final Map<Class<?>, JsonType<?>> adapterCache = new ConcurrentHashMap<>();

  public @Nullable Object convert(@Nullable Object value,
          @NonNull Type targetType) {
    if (value == null) {
      return null;
    }

    if (targetType instanceof Class<?> targetClass && targetClass == MapInput.class) {
      return toMapInput(value);
    }

    if (targetType instanceof Class<?> targetClass) {
      return convertToClass(value, targetClass);
    }

    return convertToGeneric(value, targetType);
  }

  private Object toMapInput(Object value) {
    if (value instanceof MapInput mapInput) {
      return mapInput;
    }
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return MapInput.fromMap(typed);
    }
    return value;
  }

  private Object convertToClass(Object value, Class<?> target) {
    if (target.isInstance(value)) {
      return value;
    }

    if (target == String.class) {
      return String.valueOf(value);
    }

    if (target.isPrimitive() || isWrapper(target)) {
      return convertPrimitive(value, target);
    }

    if (target.isEnum()) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Class<? extends Enum> enumClass = target.asSubclass(Enum.class);
      return Enum.valueOf(enumClass, String.valueOf(value));
    }

    if (target.isArray()) {
      return convertArray(value, target.getComponentType());
    }

    if (target.isRecord()) {
      return convertRecord(value, target);
    }

    if (Map.class.isAssignableFrom(target) && value instanceof Map) {
      return value;
    }

    if (Collection.class.isAssignableFrom(target) && value instanceof Collection) {
      return value;
    }

    return convertWithJackson(value, target);
  }

  private Object convertToGeneric(Object value, java.lang.reflect.Type targetType) {
    JavaType javaType = objectMapper.constructType(targetType);
    Class<?> rawClass = javaType.getRawClass();

    if (List.class.isAssignableFrom(rawClass) && value instanceof List<?> list) {
      JavaType elementType = javaType.getContentType();
      List<Object> result = new ArrayList<>(list.size());
      for (Object element : list) {
        result.add(objectMapper.convertValue(element, elementType));
      }
      return result;
    }

    if (Map.class.isAssignableFrom(rawClass) && value instanceof Map) {
      return value;
    }

    if (Collection.class.isAssignableFrom(rawClass) && value instanceof Collection) {
      return value;
    }

    return objectMapper.convertValue(value, javaType);
  }

  private Object convertRecord(Object value, Class<?> target) {
    if (!(value instanceof Map<?, ?> source)) {
      throw new IllegalArgumentException(
              "Record " + target.getName() + " requires a Map input, got "
                      + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> typed = (Map<String, Object>) source;

    if (target.isAnnotationPresent(Json.class)) {
      JsonType<?> adapter = adapterCache.computeIfAbsent(target, jsonb::type);
      return adapter.fromObject(typed);
    }

    return convertWithJackson(typed, target);
  }

  private Object convertArray(Object value, Class<?> componentType) {
    if (!(value instanceof Collection<?> collection)) {
      throw new IllegalArgumentException(
              "Array target requires a Collection input, got " + value.getClass().getName());
    }

    JavaType arrayType = objectMapper.getTypeFactory().constructArrayType(componentType);
    return objectMapper.convertValue(collection, arrayType);
  }

  private Object convertWithJackson(Object value, Class<?> target) {
    try {
      return objectMapper.convertValue(value, target);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
              "Cannot convert " + value.getClass().getName() + " to " + target.getName(), e);
    }
  }

  private Object convertPrimitive(Object value, Class<?> target) {
    if (target == boolean.class || target == Boolean.class) {
      return Boolean.parseBoolean(String.valueOf(value));
    }
    if (target == byte.class || target == Byte.class) {
      return ((Number) value).byteValue();
    }
    if (target == short.class || target == Short.class) {
      return ((Number) value).shortValue();
    }
    if (target == int.class || target == Integer.class) {
      return ((Number) value).intValue();
    }
    if (target == long.class || target == Long.class) {
      return ((Number) value).longValue();
    }
    if (target == float.class || target == Float.class) {
      return ((Number) value).floatValue();
    }
    if (target == double.class || target == Double.class) {
      return ((Number) value).doubleValue();
    }
    if (target == char.class || target == Character.class) {
      return String.valueOf(value).charAt(0);
    }
    throw new IllegalArgumentException("Unsupported primitive type: " + target.getName());
  }

  private boolean isWrapper(Class<?> type) {
    return type == Boolean.class
            || type == Byte.class
            || type == Short.class
            || type == Integer.class
            || type == Long.class
            || type == Float.class
            || type == Double.class
            || type == Character.class;
  }
}

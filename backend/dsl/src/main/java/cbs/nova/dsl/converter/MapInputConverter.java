package cbs.nova.dsl.converter;

import cbs.nova.dsl.model.MapInput;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class MapInputConverter {

  private final AvajeMapConverter avajeMapConverter;

  //TODO: replace with a correspondent bean injection instead
  @Deprecated(forRemoval = true)
  public MapInputConverter() {
    this(AvajeMapConverter.create());
  }

  public @Nullable Object convert(@Nullable Object value, @NonNull Type targetType) {
    if (value == null) {
      return null;
    }

    if (targetType instanceof Class<?> targetClass && targetClass == MapInput.class) {
      return toMapInput(value);
    }

    if (targetType instanceof Class<?> targetClass) {
      return convertToClass(value, targetClass);
    }

    if (targetType instanceof ParameterizedType parameterized) {
      return convertToParameterized(value, parameterized);
    }

    return value;
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

    throw new IllegalArgumentException(
            "Cannot convert " + value.getClass().getName() + " to " + target.getName());
  }

  private Object convertToParameterized(Object value, ParameterizedType parameterized) {
    Type rawType = parameterized.getRawType();
    if (!(rawType instanceof Class<?> rawClass)) {
      return value;
    }

    if (List.class.isAssignableFrom(rawClass) && value instanceof List<?> list) {
      Type elementType = parameterized.getActualTypeArguments()[0];
      List<Object> result = new ArrayList<>(list.size());
      for (Object element : list) {
        result.add(convert(element, elementType));
      }
      return result;
    }

    if (Map.class.isAssignableFrom(rawClass) && value instanceof Map) {
      return value;
    }

    return convertToClass(value, rawClass);
  }

  private Object convertRecord(Object value, Class<?> recordClass) {
    if (!(value instanceof Map<?, ?> source)) {
      throw new IllegalArgumentException(
              "Record " + recordClass.getName() + " requires a Map input, got "
                      + value.getClass().getName());
    }

    if (avajeMapConverter.supports(recordClass)) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) source;
      return avajeMapConverter.fromMap(typed, recordClass);
    }

    RecordComponent[] components = recordClass.getRecordComponents();
    Object[] args = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      RecordComponent component = components[i];
      Object raw = source.get(component.getName());
      args[i] = convert(raw, component.getGenericType());
    }

    try {
      Class<?>[] componentTypes = Arrays.stream(components)
              .map(RecordComponent::getType)
              .toArray(Class[]::new);
      Constructor<?> ctor = recordClass.getDeclaredConstructor(componentTypes);
      ctor.setAccessible(true);
      return ctor.newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
              "Failed to instantiate record " + recordClass.getName(), e);
    }
  }

  private Object convertArray(Object value, Class<?> componentType) {
    if (!(value instanceof Collection<?> collection)) {
      throw new IllegalArgumentException(
              "Array target requires a Collection input, got " + value.getClass().getName());
    }

    Object array = Array.newInstance(componentType, collection.size());
    int i = 0;
    for (Object element : collection) {
      Array.set(array, i++, convert(element, componentType));
    }
    return array;
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

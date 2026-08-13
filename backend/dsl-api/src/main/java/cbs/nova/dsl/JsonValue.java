package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * First-class immutable JSON value in the DSL runtime.
 *
 * <p>
 * Provides nested object/array lookup and type-aware retrieval without exposing the underlying JSON
 * library to DSL authors. The {@link #raw()} method is reserved for adapter code.
 */
public interface JsonValue {

  /**
   * Returns the object field with the given name, or a missing value if not an object or no such
   * field exists.
   */
  @NonNull
  JsonValue get(@NonNull String field);

  /**
   * Returns the array element at the given index, or a missing value if not an array or the index
   * is out of bounds.
   */
  @NonNull
  JsonValue get(int index);

  /** Returns this value as a string, or {@code null} if not present or null. */
  @Nullable
  String asString();

  /** Returns this value as an integer, or {@code null} if not a numeric value. */
  @Nullable
  Integer asInt();

  /** Returns this value as a long, or {@code null} if not a numeric value. */
  @Nullable
  Long asLong();

  /** Returns this value as a double, or {@code null} if not a numeric value. */
  @Nullable
  Double asDouble();

  /** Returns this value as a {@link BigDecimal}, or {@code null} if not a numeric value. */
  @Nullable
  BigDecimal asDecimal();

  /** Returns this value as a boolean, or {@code null} if not a boolean value. */
  @Nullable
  Boolean asBoolean();

  /** Returns {@code true} if this value is a JSON object. */
  boolean isObject();

  /** Returns {@code true} if this value is a JSON array. */
  boolean isArray();

  /** Returns {@code true} if this value is an explicit JSON {@code null}. */
  boolean isNull();

  /** Returns {@code true} if this value exists (is not missing). */
  boolean isPresent();

  /** Returns the array elements as a list, or an empty list if not an array. */
  @NonNull
  List<JsonValue> asList();

  /** Returns the object fields as a map, or an empty map if not an object. */
  @NonNull
  Map<String, JsonValue> asMap();

  /**
   * Returns the underlying JSON library value (e.g. Jackson {@code JsonNode}). This method is
   * intended for adapter code only; DSL authors should use the typed accessors above.
   */
  @Nullable
  Object raw();
}

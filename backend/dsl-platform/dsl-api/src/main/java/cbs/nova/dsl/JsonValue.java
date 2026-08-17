package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface JsonValue {

  @NonNull
  JsonValue get(@NonNull String field);

  @NonNull
  JsonValue get(int index);

  @Nullable
  String asString();

  @Nullable
  Integer asInt();

  @Nullable
  Long asLong();

  @Nullable
  Double asDouble();

  @Nullable
  BigDecimal asDecimal();

  @Nullable
  Boolean asBoolean();

  boolean isObject();

  boolean isArray();

  boolean isNull();

  boolean isPresent();

  @NonNull
  List<JsonValue> asList();

  @NonNull
  Map<String, JsonValue> asMap();

  @Nullable
  Object raw();
}

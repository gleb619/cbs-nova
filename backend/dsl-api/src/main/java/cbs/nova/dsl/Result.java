package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public sealed interface Result<T> permits Result.Success, Result.Failure {

  boolean isSuccess();
  @Nullable
  T value();
  @Nullable
  Throwable cause();

  record Success<T>(@NonNull T value) implements Result<T> {
    public boolean isSuccess() {
      return true;
    }
    public Throwable cause() {
      return null;
    }
  }

  record Failure<T>(@NonNull Throwable cause) implements Result<T> {
    public boolean isSuccess() {
      return false;
    }
    public T value() {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  @Nullable
  default <U> U as(@NonNull Class<U> type) {
    return type.cast(value());
  }

  @SuppressWarnings("unchecked")
  default @NonNull Map<String, Object> asMap() {
    Object v = value();
    if (v instanceof Map<?, ?> m)
      return (Map<String, Object>) m;
    return v != null ? Map.of("value", v) : Map.of();
  }

  static <T> Result<T> success(@NonNull T value) {
    return new Success<>(value);
  }
  static <T> Result<T> failure(@NonNull Throwable cause) {
    return new Failure<>(cause);
  }
}

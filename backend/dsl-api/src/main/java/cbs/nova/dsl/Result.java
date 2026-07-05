package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

  static <T> Result<T> success(@NonNull T value) {
    return new Success<>(value);
  }
  static <T> Result<T> failure(@NonNull Throwable cause) {
    return new Failure<>(cause);
  }
}

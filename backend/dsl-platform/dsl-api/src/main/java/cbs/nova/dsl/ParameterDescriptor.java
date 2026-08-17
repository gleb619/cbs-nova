package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ParameterDescriptor(
        @NonNull String name,
        @NonNull ParameterType type,
        @Nullable Class<?> objectType) {

  public static ParameterDescriptor ofString(@NonNull String name) {
    return new ParameterDescriptor(name, ParameterType.STRING, null);
  }

  public static ParameterDescriptor ofNumber(@NonNull String name) {
    return new ParameterDescriptor(name, ParameterType.NUMBER, null);
  }

  public static ParameterDescriptor ofBoolean(@NonNull String name) {
    return new ParameterDescriptor(name, ParameterType.BOOLEAN, null);
  }

  public static ParameterDescriptor ofObject(@NonNull String name, @NonNull Class<?> type) {
    return new ParameterDescriptor(name, ParameterType.OBJECT, type);
  }
}

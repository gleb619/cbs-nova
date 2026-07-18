package cbs.nova.dsl.codegen.model;

import org.jspecify.annotations.NonNull;

public record GeneratedSource(
        @NonNull String packageName, @NonNull String className, @NonNull String source) {

  public @NonNull String fullyQualifiedName() {
    return packageName.isEmpty() ? className : packageName + "." + className;
  }
}

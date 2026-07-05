package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ValidationException extends RuntimeException {
  private final List<String> errors;

  public ValidationException(@NonNull List<String> errors) {
    super("DSL validation failed (" + errors.size() + " error(s)): " + errors);
    this.errors = List.copyOf(errors);
  }

  public @NonNull List<String> errors() {
    return errors;
  }
}

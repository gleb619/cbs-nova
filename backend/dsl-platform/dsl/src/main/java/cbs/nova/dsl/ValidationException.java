package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.stream.Collectors;

public final class ValidationException extends RuntimeException {

  private final List<ValidationIssue> issues;

  public ValidationException(@NonNull List<String> errors) {
    this(errors.stream()
            .map(e -> new ValidationIssue(null, e))
            .collect(Collectors.toList()),
            true);
  }

  private ValidationException(@NonNull List<ValidationIssue> issues, boolean ignored) {
    super("DSL validation failed (" + issues.size() + " error(s)): "
            + issues.stream().map(ValidationIssue::message).toList());
    this.issues = List.copyOf(issues);
  }

  /**
   * Creates a structured exception from coded validation issues.
   */
  public static ValidationException of(@NonNull List<ValidationIssue> issues) {
    return new ValidationException(List.copyOf(issues), true);
  }

  /**
   * Returns the structured validation issues, each with a stable code.
   */
  public @NonNull List<ValidationIssue> issues() {
    return issues;
  }

  /**
   * Backwards-compatible view of the validation messages.
   */
  public @NonNull List<String> errors() {
    return issues.stream().map(ValidationIssue::message).toList();
  }
}

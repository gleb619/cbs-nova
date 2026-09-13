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

  public static ValidationException of(@NonNull List<ValidationIssue> issues) {
    return new ValidationException(List.copyOf(issues), true);
  }

  public @NonNull List<ValidationIssue> issues() {
    return issues;
  }

  public @NonNull List<String> errors() {
    return issues.stream().map(ValidationIssue::message).toList();
  }
}

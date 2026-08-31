package cbs.nova.starter.model;

/**
 * Tagged union for runtime handler results — exactly one of {@link #value()} or {@link #error()} is
 * populated. Lets {@code DslRuntimeService} stay HTTP-agnostic while the handler maps the outcome
 * to a {@code ServerResponse}.
 */
public record RuntimeOutcome(boolean success, Object value, ErrorResponse error, boolean replayed) {

  public static RuntimeOutcome ok(Object value) {
    return new RuntimeOutcome(true, value, null, false);
  }

  public static RuntimeOutcome error(ErrorResponse error) {
    return new RuntimeOutcome(false, null, error, false);
  }

  public static RuntimeOutcome okReplayed(Object value) {
    return new RuntimeOutcome(true, value, null, true);
  }
}

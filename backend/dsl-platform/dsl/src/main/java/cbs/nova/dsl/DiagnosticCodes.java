package cbs.nova.dsl;

/**
 * Stable diagnostic codes emitted by the DSL semantic validator.
 *
 * <p>
 * Codes are bare {@code SCREAMING_SNAKE_CASE} strings. They identify the error kind so callers can
 * branch programmatically; the human message may change independently. Codes are preserved across
 * releases but the exact wording of {@code message} is not part of the compatibility contract.
 * </p>
 *
 * <p>
 * javac codes (e.g. {@code compiler.err.cannot.find.symbol}) are passed through verbatim on syntax
 * errors and are JDK-versioned.
 * </p>
 */
public final class DiagnosticCodes {

  private DiagnosticCodes() {
  }

  public static final String BLANK_PROCESS_NAME = "BLANK_PROCESS_NAME";
  public static final String BLANK_TRANSACTION_NAME = "BLANK_TRANSACTION_NAME";
  public static final String BLANK_FUNCTION_NAME = "BLANK_FUNCTION_NAME";
  public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
  public static final String UNKNOWN_HELPER = "UNKNOWN_HELPER";
  public static final String CIRCULAR_DEPENDENCY = "CIRCULAR_DEPENDENCY";
}

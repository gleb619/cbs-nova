package cbs.dsl.builder;

/**
 * Entry point for the event definition DSL.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * EventDefinition event = EventDsl.event("LOAN_SUBMIT")
 *     .transaction("VALIDATE_CUSTOMER")
 *     .transaction("CHECK_CREDIT_SCORE")
 *     .build();
 * }</pre>
 */
public final class EventDsl {

  private EventDsl() {}

  /**
   * Creates a new event builder with the given code.
   *
   * @param code the event code
   * @return a new event builder
   */
  public static EventBuilder event(String code) {
    return new EventBuilder(code);
  }
}

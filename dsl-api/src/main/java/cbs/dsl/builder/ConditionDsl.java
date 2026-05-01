package cbs.dsl.builder;

/**
 * Entry point for the condition definition DSL.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ConditionDefinition condition = ConditionDsl.condition("ACCOUNT_ACTIVE")
 *     .requiredParam("accountCode")
 *     .evaluate(ctx -> new ConditionOutput(true))
 *     .build();
 * }</pre>
 */
public final class ConditionDsl {

  private ConditionDsl() {}

  /**
   * Creates a new condition builder with the given code.
   *
   * @param code the condition code
   * @return a new condition builder
   */
  public static ConditionBuilder condition(String code) {
    return new ConditionBuilder(code);
  }
}

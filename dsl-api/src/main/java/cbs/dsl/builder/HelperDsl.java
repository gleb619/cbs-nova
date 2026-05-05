package cbs.dsl.builder;

/**
 * Entry point for the helper definition DSL.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * DslObject helper = HelperDsl.helper("LOAN_CONDITIONS_BY_ID")
 *     .execute(input -> new HelperOutput(Map.of("result", "ok")))
 *     .build();
 * }</pre>
 */
public final class HelperDsl {

  private HelperDsl() {}

  /**
   * Creates a new helper builder with the given code.
   *
   * @param code the helper code
   * @return a new helper builder
   */
  public static HelperBuilder helper(String code) {
    return new HelperBuilder(code);
  }
}

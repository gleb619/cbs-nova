package cbs.dsl.builder;

/**
 * Entry point for the mass operation definition DSL.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * DslObject mo = MassOperationDsl.massOperation("INTEREST_CHARGE")
 *     .category("CREDITS")
 *     .item(ctx -> { ... })
 *     .build();
 * }</pre>
 */
public final class MassOperationDsl {

  private MassOperationDsl() {}

  /**
   * Creates a new mass operation builder with the given code.
   *
   * @param code the mass operation code
   * @return a new mass operation builder
   */
  public static MassOperationBuilder massOperation(String code) {
    return new MassOperationBuilder(code);
  }
}

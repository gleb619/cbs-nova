package cbs.dsl.builder;

/**
 * Entry point for the transaction definition DSL.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * TransactionDefinition tx = TransactionDsl.transaction("DEBIT_ACCOUNT")
 *     .requiredParam("accountCode")
 *     .execute(ctx -> new TransactionOutput(Map.of("txId", "123")))
 *     .build();
 * }</pre>
 */
public final class TransactionDsl {

  private TransactionDsl() {}

  /**
   * Creates a new transaction builder with the given code.
   *
   * @param code the transaction code
   * @return a new transaction builder
   */
  public static TransactionBuilder transaction(String code) {
    return new TransactionBuilder(code);
  }
}

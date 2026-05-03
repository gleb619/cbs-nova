package cbs.dsl.api;

import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Defines a transaction — a unit of work with preview, execute, and rollback phases.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code transaction { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface TransactionDefinition extends DslDefinition {

  /**
   * Canonical code used to look up this transaction in the registry.
   *
   * @return the transaction code
   */
  String getCode();

  /**
   * Optional display name for this transaction. Used to distinguish DSL overrides from the
   * underlying implementation class/bean identified by {@link #getCode()}. When set, the DSL block
   * is treated as a named override of the bean registered under the code.
   *
   * @return the display name, or {@code null}
   */
  default String getName() {
    return null;
  }

  /**
   * List of parameter definitions declared in the {@code parameters { }} block. Used for validation
   * and documentation purposes.
   *
   * @return the parameter definitions
   */
  default List<ParameterDefinition> getParameters() {
    return Collections.emptyList();
  }

  /**
   * Optional context enrichment block that runs before each phase block (preview, execute,
   * rollback). Allows transactions to enrich the context with additional data before execution.
   *
   * @return the context block
   */
  default Consumer<TransactionContext> getContextBlock() {
    return ctx -> {};
  }

  /**
   * Preview phase — validates inputs without mutating state.
   *
   * @param input the transaction input
   * @return the transaction output
   */
  TransactionOutput preview(TransactionInput input);

  /**
   * Execute phase — performs the business logic.
   *
   * @param input the transaction input
   * @return the transaction output
   */
  TransactionOutput execute(TransactionInput input);

  /**
   * Rollback phase — compensates a previously executed transaction.
   *
   * @param input the transaction input
   * @return the transaction output
   */
  TransactionOutput rollback(TransactionInput input);
}

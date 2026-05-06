package cbs.dsl.api;

import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;

import cbs.dsl.builder.TransactionDslObject;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Defines a transaction — a unit of work with preview, execute, and rollback phases.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code transaction { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface TransactionDefinition extends DslDefinition<TransactionDslObject> {

  /**
   * Canonical code used to look up this transaction in the registry.
   *
   * @return the transaction code
   */
  String getCode();

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

  /**
   * Returns the DSL object representing this definition.
   *
   * @return the DSL object, or {@code null} if not available
   */
  default TransactionDslObject dsl() {
    throw new NullPointerException("Dsl object not added");
  }
}

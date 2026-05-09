package cbs.dsl.api;

import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;

/**
 * Defines a transaction — a unit of work with preview, execute, and rollback phases.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code transaction { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
@FunctionalInterface
public interface TransactionDefinition extends StandardDslDefinition {

  TransactionOutput execute(TransactionInput input);

  default TransactionOutput rollback(TransactionInput input) {
    return input.asOutput();
  }
}

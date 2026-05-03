package cbs.dsl.script

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.runtime.TransactionBuilder

abstract class TransactionDslScope {
  internal var registeredTransaction: TransactionDefinition? = null

  fun transaction(code: String, block: TransactionBuilder.() -> Unit): TransactionDefinition {
    require(registeredTransaction == null) { "Only one transaction block is allowed per script" }
    val def = TransactionBuilder(code).apply(block)
    registeredTransaction = def
    return def
  }
}

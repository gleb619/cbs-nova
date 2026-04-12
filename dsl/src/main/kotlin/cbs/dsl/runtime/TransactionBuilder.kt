package cbs.dsl.runtime

import cbs.dsl.api.TransactionDefinition
import cbs.dsl.api.context.TransactionContext

class TransactionBuilder(override val code: String) : TransactionDefinition {
  private var _preview: ((TransactionContext) -> Unit)? = null
  private var _execute: ((TransactionContext) -> Unit)? = null
  private var _rollback: ((TransactionContext) -> Unit)? = null

  override fun preview(ctx: TransactionContext) {
    _preview?.invoke(ctx)
  }

  override fun execute(ctx: TransactionContext) {
    _execute?.invoke(ctx)
      ?: error("Transaction '$code' has no execute block defined")
  }

  override fun rollback(ctx: TransactionContext) {
    _rollback?.invoke(ctx)
  }

  fun preview(block: (TransactionContext) -> Unit) {
    _preview = block
  }

  fun execute(block: (TransactionContext) -> Unit) {
    _execute = block
  }

  fun rollback(block: (TransactionContext) -> Unit) {
    _rollback = block
  }
}

fun transaction(code: String, block: TransactionBuilder.() -> Unit): TransactionDefinition =
  TransactionBuilder(code).apply(block)

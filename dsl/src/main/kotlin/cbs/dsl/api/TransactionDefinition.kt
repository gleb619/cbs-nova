package cbs.dsl.api

import cbs.dsl.api.context.TransactionContext

interface TransactionDefinition {
  val code: String
  fun preview(ctx: TransactionContext): Unit
  fun execute(ctx: TransactionContext): Unit
  fun rollback(ctx: TransactionContext): Unit
}

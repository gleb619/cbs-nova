package cbs.dsl.api

import cbs.dsl.api.context.TransactionContext

interface TransactionDefinition {
  val code: String
  fun preview(ctx: TransactionContext)
  fun execute(ctx: TransactionContext)
  fun rollback(ctx: TransactionContext)
}

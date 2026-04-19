package cbs.dsl.api

import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext
import cbs.dsl.api.context.TransactionsScope

interface EventDefinition {
  val code: String
  val contextBlock: (EnrichmentContext) -> Unit
  val displayBlock: (FinishContext) -> Unit
  val transactionsBlock: (suspend TransactionsScope.() -> Unit)?
  val finishBlock: (FinishContext) -> Unit
}

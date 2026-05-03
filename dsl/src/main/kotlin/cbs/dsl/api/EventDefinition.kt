package cbs.dsl.api

import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext

interface EventDefinition {
  val code: String
  val contextBlock: (EnrichmentContext) -> Unit
  val displayBlock: (FinishContext) -> Unit
  val transactionsBlock: List<TransactionDefinition>
  val finishBlock: (FinishContext) -> Unit
}

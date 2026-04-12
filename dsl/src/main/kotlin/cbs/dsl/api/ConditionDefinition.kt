package cbs.dsl.api

import cbs.dsl.api.context.TransactionContext

interface ConditionDefinition {
  val code: String
  val predicate: (TransactionContext) -> Boolean
}
